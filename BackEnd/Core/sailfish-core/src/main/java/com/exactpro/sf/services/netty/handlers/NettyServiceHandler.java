/******************************************************************************
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
package com.exactpro.sf.services.netty.handlers;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.common.messages.IMessage;
import com.exactpro.sf.common.messages.IMessageFactory;
import com.exactpro.sf.common.util.EvolutionBatch;
import com.exactpro.sf.services.IServiceHandler;
import com.exactpro.sf.services.ISession;
import com.exactpro.sf.services.ServiceHandlerException;
import com.exactpro.sf.services.ServiceHandlerRoute;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

@SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
public class NettyServiceHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(NettyServiceHandler.class);

    private static final Delimiter DELIMITER = Delimiter.INSTANCE;

	// FIXME: IServiceHandler throws exceptions!

    private final IServiceHandler serviceHandler;

    private final ISession session;

    private final boolean evolutionSupport;
    private final IMessageFactory messageFactory;

    private volatile EvolutionBatch batch = new EvolutionBatch(1);

    public NettyServiceHandler(IServiceHandler serviceHandler, ISession session) {
        this(serviceHandler, session, null, false);
    }

    public NettyServiceHandler(IServiceHandler serviceHandler, ISession session, IMessageFactory messageFactory, boolean evolutionSupport) {
        this.serviceHandler = serviceHandler;
        this.session = session;
        this.messageFactory = messageFactory;
        this.evolutionSupport = evolutionSupport;
        if (evolutionSupport) {
            Objects.requireNonNull(messageFactory, "Message factory cannot be null if evolution support is enabled");
        }
    }


	@Override
    public void write(ChannelHandlerContext context, Object msg, ChannelPromise promise) throws Exception {
    	if (msg instanceof IMessage) {
    		IMessage imsg = (IMessage) msg;
            serviceHandler.putMessage(session, imsg.getMetaData().isAdmin() ? ServiceHandlerRoute.TO_ADMIN : ServiceHandlerRoute.TO_APP, imsg);
    	}
    	context.write(msg, promise);
    }

	@Override
    public void close(ChannelHandlerContext context, ChannelPromise promise) throws Exception {
    	serviceHandler.sessionClosed(session);
    	context.close(promise);
    }

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered is now inactive and reached its
     * end of lifetime.
     */
    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        context.fireChannelInactive();
    }


    @Override
    public void channelRead(ChannelHandlerContext context, Object msg) throws Exception {
    	if (msg instanceof IMessage) {
            processIncomingMessage((IMessage)msg);
        } else if (msg == DELIMITER) {
            processEvolutionBatchIfNeeded(batch);
        }

        if (msg != DELIMITER) {
            context.fireChannelRead(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    	serviceHandler.exceptionCaught(session, cause);
        context.fireExceptionCaught(cause);
    }

    private void processIncomingMessage(IMessage msg) throws ServiceHandlerException {
        if (evolutionSupport) {
            EvolutionBatch batch = this.batch;
            if (batch == null) {
                logger.warn("Batch is null during processing received message {}", msg.getName());
            } else {
                batch.addMessage(msg);
            }
        } else {
            putReceivedMessage(msg);
        }
    }

    private void putReceivedMessage(IMessage msg) throws ServiceHandlerException {
        serviceHandler.putMessage(session, msg.getMetaData().isAdmin() ? ServiceHandlerRoute.FROM_ADMIN : ServiceHandlerRoute.FROM_APP, msg);
    }

    private void processEvolutionBatchIfNeeded(EvolutionBatch batch) throws ServiceHandlerException {
        if (!evolutionSupport || batch.isEmpty()) {
            return;
        }
        IMessage batchMessage = batch.toMessage(messageFactory);
        logger.debug("Storing evolution batch with size {}", batch.size());
        putReceivedMessage(batchMessage);
        this.batch = new EvolutionBatch(1);
    }
}
