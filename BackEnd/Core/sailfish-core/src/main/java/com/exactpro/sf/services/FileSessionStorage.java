/*******************************************************************************
 * Copyright 2009-2018 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.exactpro.sf.services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.configuration.workspace.FolderType;
import com.exactpro.sf.configuration.workspace.IWorkspaceDispatcher;
import com.exactpro.sf.util.DateTimeUtility;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

public class FileSessionStorage {

    private final Logger logger = LoggerFactory.getLogger(getClass().getName() + "@" + Integer.toHexString(hashCode()));

    private static final String STORAGE = "session_manager_storage.json";
    private static final ObjectReader jsonReader = new ObjectMapper().readerFor(new MapTypeReference());
    private static final ObjectWriter jsonWriter = new ObjectMapper().writer();

    private final File targetStorage;
    private final Map<String, TimestampedValue<?>> metadataMap;
    private final Predicate<Long> lifetimePredicate = new DailyLifetimeInvalidator();


    public FileSessionStorage(IWorkspaceDispatcher workspaceDispatcher, String protocol, String sessionId) throws IOException {

        String[] path = { "storage", protocol, sessionId, STORAGE };
        targetStorage = workspaceDispatcher.getOrCreateFile(FolderType.ROOT, path);

        Map<String, TimestampedValue<?>> tmpMap;

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(targetStorage))) {
            tmpMap = jsonReader.readValue(inputStream);
        } catch (Exception e) {
            logger.error("Can't parse session storage", e);
            tmpMap = new HashMap<>();
        }

        tmpMap = tmpMap.entrySet().stream().filter(entry -> lifetimePredicate.test(entry.getValue().getTimestamp()))
                .collect(Collectors.toConcurrentMap(Entry::getKey, Entry::getValue));

        metadataMap = tmpMap;
    }

    public void putSessionProperties(Map<String, ?> properties) {

        properties.forEach((k, v) -> putSessionProperty(k, v));
    }

    public void flush() throws Exception {

        String json = jsonWriter.withDefaultPrettyPrinter().writeValueAsString(metadataMap);

        synchronized (targetStorage) {
            Files.write(targetStorage.toPath(), json.getBytes(StandardCharsets.UTF_8));
        }
    }

    public <T> T readSessionProperty(String key) {
        TimestampedValue<T> tval = (TimestampedValue<T>) metadataMap.get(key);
        return (tval != null) && lifetimePredicate.test(tval.getTimestamp()) ? tval.value : null;
    }

    public <T> void putSessionProperty(String key, T value) {

        if ( value != null && !checkType(value.getClass())) {
            throw new UnsupportedOperationException(value.getClass().getCanonicalName() + " is not supported to store");
        }

        metadataMap.put(key, new TimestampedValue<>(value));
    }

    private boolean checkType(Class<?> clazz) {
        return clazz == String.class || clazz == BigDecimal.class || ClassUtils.isPrimitiveWrapper(clazz);
    }

    private static class MapTypeReference extends TypeReference<Map<String, TimestampedValue<?>>> {
    }

    private static final class TimestampedValue<T> {

        private final long timestamp;
        private final T value;

        public TimestampedValue() {
            //for jackson
            timestamp = System.currentTimeMillis();
            value = null;
        }

        TimestampedValue(T value) {
            this.value = value;
            timestamp = System.currentTimeMillis();
        }

        public long getTimestamp() {
            return timestamp;
        }

        public T getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    private static class DailyLifetimeInvalidator implements Predicate<Long> {

        // start of the current day in epoch millis
        private final long leftBarrier = DateTimeUtility.getMillisecond(DateTimeUtility.nowLocalDate());
        private final long rightBarrier = leftBarrier + TimeUnit.DAYS.toMillis(1);

        @Override
        public boolean test(Long unixTime) {
            return leftBarrier < unixTime && rightBarrier > unixTime;
        }
    }
}
