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
package com.exactpro.sf.services.itch;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.DummySession;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.AbstractProtocolDecoderOutput;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.common.impl.messages.MapMessage;
import com.exactpro.sf.common.messages.IMessage;
import com.exactpro.sf.configuration.suri.SailfishURI;
import com.exactpro.sf.services.MockProtocolDecoderOutput;
import com.exactpro.sf.util.DateTimeUtility;
import com.exactpro.sf.util.TestITCHHelper;

public class TestITCHCodecPositive extends TestITCHHelper {

    private static final Logger logger = LoggerFactory.getLogger(TestITCHCodecPositive.class);

	@BeforeClass
	public static void setUpClass(){
		logger.info("Start positive tests of ITCH codec");
	}

	@AfterClass
	public static void tearDownClass(){
		logger.info("Finish positive tests of ITCH codec");
	}

	/**
     * Encode and decode AddOrder message with ITCHPreprocessor. Encode and decode OrderExecuted after and compare
	 * AddOrder's InstrumntID field from original message and OrderExecuted's FakeInstrumentID field from result message
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testITCHPreprocessor(){
		try{
			IMessage messageAddOrder = getMessageCreator().getAddOrder();

            ITCHCodec codec = new ITCHCodec();
            ITCHCodecSettings settings = new ITCHCodecSettings();
            settings.setMsgLength(1);
            settings.setDictionaryURI(SailfishURI.unsafeParse("ITCH"));
            codec.init(serviceContext, settings, getMessageHelper().getMessageFactory(), getMessageHelper().getDictionaryStructure());

            messageAddOrder = getMessageHelper().prepareMessageToEncode(messageAddOrder, null);
			IMessage decodedMessage= decode(encode(messageAddOrder,null),codec);

			IMessage orderExecuted = getMessageCreator().getOrderExecuted();
			orderExecuted = getMessageHelper().prepareMessageToEncode(orderExecuted, null);
			decodedMessage=decode(encode(orderExecuted,null),codec);
			Assert.assertTrue( "Object must be instance of IMessage.", decodedMessage instanceof IMessage);
		    Assert.assertTrue( "Object must be instance of MapMessage.", decodedMessage instanceof MapMessage);

            List<IMessage> listResult = (List<IMessage>) decodedMessage.getField(ITCHMessageHelper.SUBMESSAGES_FIELD_NAME);
            List<IMessage> original = (List<IMessage>) messageAddOrder.getField(ITCHMessageHelper.SUBMESSAGES_FIELD_NAME);
		    Assert.assertEquals(2, listResult.size());
		    Assert.assertEquals(2, original.size());
            Assert.assertEquals(original.get(1).<Object>getField("InstrumentID"), listResult.get(1).<Object>getField("FakeInstrumentID"));
	    }catch(Exception e){
	    	logger.error(e.getMessage(),e);
	    	Assert.fail(e.getMessage());
	    }
	}


	/**
	 * Try to encode and decode UnitHeader message. Compare original and result message.
	 */
	@Test
	public void testEncodeDecodeUnitHeader(){
		IMessage messageHeader = getUnitHeader((short)0);
		try{
	    	IMessage decodedMessage=decode(encode(messageHeader,null),null);
		    Assert.assertTrue( "Object must be instance of IMessage.", decodedMessage instanceof IMessage);
		    Assert.assertTrue( "Object must be instance of MapMessage.", decodedMessage instanceof MapMessage);
		    @SuppressWarnings("unchecked")
            IMessage resultUnitHeader = ((List<IMessage>) decodedMessage.getField(ITCHMessageHelper.SUBMESSAGES_FIELD_NAME)).get(0);
		    Assert.assertTrue("UnitHeader messages must be equal. Original message:"+messageHeader+"; \n"
		    		+ "Result message:"+resultUnitHeader, resultUnitHeader.compare(messageHeader));
	    }catch(Exception e){
	    	logger.error(e.getMessage(),e);
	    	Assert.fail(e.getMessage());
	    }
	}

	/**
	 * Try to encode and decode list of messages: UnitHeader, Time, AddOrderOneByteLength. Compare result and check
	 * UnitHeader, Seconds in Time message and Nanosecond in AddOrderOneByteLength message.
	 * Message create without prepareMessageToEncode(...) method.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testEncodeDecodeListMessages(){
		IMessage messageHeader = getUnitHeader((short)2);
		long seconds = 60 * 4 + 8;
		long nanoseconds=10L;
	    IMessage messageTime = getMessageCreator().getTime(seconds);
	    IMessage messageAddOrder = getMessageCreator().getAddOrderOneByteLength(nanoseconds);
	    List<IMessage> list = new ArrayList<>();
	    list.add(messageHeader);
	    list.add(messageTime);
	    list.add(messageAddOrder);
	    IMessage messageList = getMessageCreator().getMessageList(list);
	    try{
	    	IMessage decodedMessage=decode(encode(messageList,null),null);
		    Assert.assertTrue( "Object must be instance of IMessage.", decodedMessage instanceof IMessage);
		    Assert.assertTrue( "Object must be instance of MapMessage.", decodedMessage instanceof MapMessage);

            List<IMessage> listResult = (List<IMessage>) decodedMessage.getField(ITCHMessageHelper.SUBMESSAGES_FIELD_NAME);
            List<IMessage> original = (List<IMessage>) messageList.getField(ITCHMessageHelper.SUBMESSAGES_FIELD_NAME);
		    Assert.assertEquals(3, listResult.size());
		    Assert.assertEquals(3, original.size());
		    Assert.assertTrue("UnitHeader messages must be equal. Original message:"+original.get(0)+"; \n"
		    		+ "Result message:"+listResult.get(0), listResult.get(0).compare(original.get(0)));
		    Assert.assertTrue("Field \"Seconds\" can not be null.",listResult.get(1).getField("Seconds")!=null);
		    Assert.assertTrue("Field \"Seconds\" must be equal. Original message:"+original.get(1)+"; \n"
		    		+ "Result message:"+listResult.get(1), original.get(1).getField("Seconds").
		    			equals(listResult.get(1).getField("Seconds")));
		    Assert.assertTrue("Field \"Nanosecond\" can not be null.",listResult.get(2).getField("Nanosecond")!=null);
		    Assert.assertTrue("Field \"Nanosecond\" must be equal. Original message:"+original.get(2)+"; \n"
		    		+ "Result message:"+listResult.get(2), original.get(2).getField("Nanosecond").
		    			equals(listResult.get(2).getField("Nanosecond")));
	    }catch(Exception e){
	    	logger.error(e.getMessage(),e);
	    	Assert.fail(e.getMessage());
	    }
	}

	/**
	 * Try to encode and decode message SecurityClassTickMatrix with 2 TicksGroup's messages. Compare result and check
	 * UnitHeader and both TicksGroup's messages. Message create with prepareMessageToEncode(...) method.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testEncodeDecodeSecurityClassTickMatrix(){
		List<IMessage> groups = new ArrayList<>();
		IMessage group = getMessageCreator().getTicksGroup(0.0,0.0,0.0);
		groups.add(group);
		group = getMessageCreator().getTicksGroup(1.0,1.0,1.0);
		groups.add(group);
        IMessage message = getMessageCreator().getSecurityClassTickMatrix(groups);
		IMessage messageList = getMessageHelper().prepareMessageToEncode(message, null);
		try{
			IMessage decodedMessage=decode(encode(messageList,null),null);
			Assert.assertTrue( "Object must be instance of IMessage.", decodedMessage instanceof IMessage);
		    Assert.assertTrue( "Object must be instance of MapMessage.", decodedMessage instanceof MapMessage);

            List<IMessage> listResult = (List<IMessage>) decodedMessage.getField(ITCHMessageHelper.SUBMESSAGES_FIELD_NAME);
            List<IMessage> original = (List<IMessage>) messageList.getField(ITCHMessageHelper.SUBMESSAGES_FIELD_NAME);
		    Assert.assertEquals(2, listResult.size());
		    Assert.assertEquals(2, original.size());
		    Assert.assertTrue("UnitHeader messages must be equal. Original message:"+original.get(0)+"; \n"
		    		+ "Result message:"+listResult.get(0), listResult.get(0).compare(original.get(0)));
		    List<IMessage> ticksGroupOriginal=(List<IMessage>)original.get(1).getField("TicksGroup");
		    List<IMessage> ticksGroupResult=(List<IMessage>)listResult.get(1).getField("TicksGroup");
		    Assert.assertTrue("First TicksGroup messages must be equal. Original message:"+ticksGroupOriginal.get(0)+"; \n"
		    		+ "Result message:"+ticksGroupResult.get(0), ticksGroupOriginal.get(0).compare(ticksGroupResult.get(0)));
		    Assert.assertTrue("Second TicksGroup messages must be equal. Original message:"+ticksGroupOriginal.get(1)+"; \n"
		    		+ "Result message:"+ticksGroupResult.get(1), ticksGroupOriginal.get(1).compare(ticksGroupResult.get(1)));
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Try to encode LoginRequest and print result bytes into console.
	 */
	@Ignore@Test
	public void testEncodeMessageLoginRequest(){
		try{
			IMessage list = getMessageHelper().prepareMessageToEncode(getMessageCreator().getLoginRequest(), null);
			Object lastMessage = encode(list,null);
	        byte[] asd = ((IoBuffer)lastMessage).array();
	        int limit = ((IoBuffer)lastMessage).limit();
	        byte[] bytes = Arrays.copyOf(asd, limit );
            for(int i = 0; i < bytes.length; i++) {
                System.out.println(bytes[i]);
            }
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Try to encode SymbolDirectory and print result bytes into console.
	 */
	@Ignore@Test
	public void testEncodeMessageSymbolDirectory(){
		try{
			IMessage addOrder = getMessageCreator().getAddOrder();
			IMessage list = getMessageHelper().prepareMessageToEncode(addOrder, null);
			Object lastMessage = encode(list,null);
			byte[] asd = ((IoBuffer)lastMessage).array();
			int limit = ((IoBuffer)lastMessage).limit();
			byte[] bytes = Arrays.copyOf(asd, limit );
            for(int i = 0; i < bytes.length; i++) {
                System.out.println(bytes[i]);
            }
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Try to decode AddOrderOneByteLength from byte[] and print result message into console.
	 */
	@Ignore@Test
	public void testDecodeMessageAddOrderOneByteLength(){
        ITCHCodec codec = (ITCHCodec) getMessageHelper().getCodec(serviceContext);
        int[] array = {0x2E, 0x00, 0x01, 0x31, 0x10, 0x05, 0x00, 0x00, 0x2E, 0x41, 0xE0, 0x98, 0xC3, 0x22, 0x0E, 0x00,
         0x00, 0x40, 0xC4, 0xD4, 0x57, 0x01, 0x42, 0xE8, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3D,
         0xE6, 0x46, 0x00, 0x00, 0x00, 0x00, 0x4E, 0x72, 0x53, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
         0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        byte[] b = new byte[array.length];
		for (int i=0; i<array.length; i++)
		{
			b[i] = (byte) array[i];
		}

		IoBuffer toDecode = IoBuffer.wrap( b );
		toDecode.order(ByteOrder.LITTLE_ENDIAN);
		toDecode.position(0);

		IoSession decodeSession = new DummySession();
		MockProtocolDecoderOutput decoderOutput = new MockProtocolDecoderOutput();
		try{
			codec.decode( decodeSession, toDecode, decoderOutput );
			System.out.println((IMessage) decoderOutput.getMessageQueue().element());
			Assert.assertEquals( "No message decoded", 1, decoderOutput.getMessageQueue().size());
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			Assert.fail(e.getMessage());
		}
	}

	/**
	 * Try to decode SymbolDirectory from byte[] and print result message into console.
	 */
	@Ignore@Test
	public void testDecodeMessageSymbolDirectory(){
        ITCHCodec codec = (ITCHCodec) getMessageHelper().getCodec(serviceContext);
        // there are two packets in this array
		int[] array = {
				0x64, 0x00, // Size
				0x01,       // Count
				0x31,       // MD Group
				0x00, 0x00, 0x00, 0x00, // SeqNumber
				0x5C, 0x52, 0xF0, 0x9D, 0xE6, 0x2B, 0xA4, 0x42, 0x0F, 0x00, 0x00, 0x00, 0x20, 0x49, 0x54, 0x31, 0x30, 0x30, 0x31, 0x30, 0x30, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x54, 0x41, 0x48, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x45, 0x55, 0x52, 0x00, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x64, 0x00, 0x01, 0x31, 0x00, 0x00, 0x00, 0x00, 0x5C, 0x52, 0xF8, 0xE9, 0xE7, 0x2B, 0xA9, 0x42, 0x0F, 0x00, 0x00, 0x00, 0x20, 0x49, 0x54, 0x30, 0x30, 0x30, 0x30, 0x30, 0x36, 0x32, 0x30, 0x37, 0x32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x54, 0x41, 0x48, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x45, 0x55, 0x52, 0x00, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x47, 0x45, 0x4E, 0x45, 0x52, 0x41, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00};
		byte[] b = new byte[array.length];
		for (int i=0; i<array.length; i++)
		{
			b[i] = (byte) array[i];
		}

		IoBuffer toDecode = IoBuffer.wrap( b );
		toDecode.order(ByteOrder.LITTLE_ENDIAN);
		toDecode.position(0);

		IoSession decodeSession = new DummySession();
		MockProtocolDecoderOutput decoderOutput = new MockProtocolDecoderOutput();
		try{
			codec.decode( decodeSession, toDecode, decoderOutput );
			System.out.println((IMessage) decoderOutput.getMessageQueue().element());
			Assert.assertEquals( "No messages decoded", 2, decoderOutput.getMessageQueue().size());
			System.out.println("position = "+toDecode.position());
			Assert.assertEquals("Not all bytes read", 0, toDecode.remaining());
		}catch(Exception e){
			logger.error(e.getMessage(),e);
			Assert.fail(e.getMessage());
		}

	}

	@Test
    public void testBigTime() throws Exception{
        IMessage messageHeader = getUnitHeader((short)2);
        IMessage messageTime = getMessageCreator().getTime(Integer.MAX_VALUE + 1l);
        IMessage messageAddOrder = getMessageCreator().getAddOrderOneByteLength(Integer.MAX_VALUE + 2l);
        List<IMessage> list = new ArrayList<>();
        list.add(messageHeader);
        list.add(messageTime);
        list.add(messageAddOrder);
        IMessage messageList = getMessageCreator().getMessageList(list);
        IMessage decodedMessage=decode(encode(messageList,null),null);
        Assert.assertTrue( "Object must be instance of IMessage.", decodedMessage instanceof IMessage);

        List<IMessage> listResult = decodedMessage.<List<IMessage>>getField(ITCHMessageHelper.SUBMESSAGES_FIELD_NAME);
        Assert.assertEquals(3, listResult.size());
        Assert.assertEquals(DateTimeUtility.toLocalDateTime((Integer.MAX_VALUE + 1l) * 1000).plusNanos(Integer.MAX_VALUE + 2l), listResult.get(2).getField(ITCHMessageHelper.FAKE_FIELD_MESSAGE_TIME));
    }
    
    /**
     * Check that the isRejected is true for case the number of bytes read is not equal to the specified message length.
     * @throws Exception
     */
    @Test
    public void testIncorrectLength() throws Exception {
        ITCHCodec codec = (ITCHCodec)getMessageHelper().getCodec(serviceContext);
        int[] array = {
                //header
                0x78, 0x01, 0x04, 0x31, 0x00, 0x00, 0x00, 0x00,
                
                //incorrect length (msg must be rejected)
                0x5E, 0x52, 0xF0, 0x9D, 0xE6, 0x2B, 0xA4, 0x42, 0x0F, 0x00, 0x00, 0x00, 0x20, 0x49, 0x54, 0x31, 0x30, 0x30,
                0x31, 0x30, 0x30, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x54, 0x41, 0x48, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x45, 0x55, 0x52, 0x00, 0x20,
                0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                
                //correct
                0x5C, 0x52, 0xF0, 0x9D, 0xE6, 0x2B, 0xA4, 0x42, 0x0F, 0x00, 0x00, 0x00, 0x20, 0x49, 0x54, 0x31, 0x30, 0x30,
                0x31, 0x30, 0x30, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x54, 0x41, 0x48, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x45, 0x55, 0x52, 0x00, 0x20,
                0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00,
                0x00, 0x00,
                
                //incorrect length (msg must be rejected)
                0x5A, 0x52, 0xF0, 0x9D, 0xE6, 0x2B, 0xA4, 0x42, 0x0F, 0x00, 0x00, 0x00, 0x20, 0x49, 0x54, 0x31, 0x30, 0x30,
                0x31, 0x30, 0x30, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x54, 0x41, 0x48, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x45, 0x55, 0x52, 0x00, 0x20,
                0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00,
                
                //correct
                0x5C, 0x52, 0xF0, 0x9D, 0xE6, 0x2B, 0xA4, 0x42, 0x0F, 0x00, 0x00, 0x00, 0x20, 0x49, 0x54, 0x31, 0x30, 0x30,
                0x31, 0x30, 0x30, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x54, 0x41, 0x48, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x45, 0x55, 0x52, 0x00, 0x20,
                0x20, 0x20, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x00, 0x00, 0x00,
                0x00, 0x00 };
        byte[] bytes = new byte[array.length];
        for (int i = 0; i < array.length; i++) {
            bytes[i] = (byte)array[i];
        }
        
        IoBuffer toDecode = IoBuffer.wrap(bytes);
        toDecode.order(ByteOrder.LITTLE_ENDIAN);
        toDecode.position(0);
        
        AbstractProtocolDecoderOutput decoderOutput = new MockProtocolDecoderOutput();
        IoSession decodeSession = new DummySession();
        codec.decode(decodeSession, toDecode, decoderOutput);
        IMessage msg = (IMessage)decoderOutput.getMessageQueue().element();
        
        List<IMessage> listMsg = msg.getField(ITCHMessageHelper.SUBMESSAGES_FIELD_NAME);
        boolean[] rejectedMessage = { false, true, false, true, false };
        for (int index = 0; index < listMsg.size(); index++) {
            Assert.assertEquals(rejectedMessage[index], listMsg.get(index).getMetaData().isRejected());
        }
    }
}
