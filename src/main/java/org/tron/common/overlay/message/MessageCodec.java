/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tron.common.overlay.message;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.server.Channel;
import org.tron.core.config.args.Args;

import java.io.IOException;
import java.util.List;


/**
 * The Netty codec which encodes/decodes RPLx frames to subprotocol Messages
 */
@Component
@Scope("prototype")
public class MessageCodec extends ByteToMessageCodec<Message> {

    private static final Logger loggerWire = LoggerFactory.getLogger("wire");
    private static final Logger loggerNet = LoggerFactory.getLogger("net");
    private Channel channel;
    private P2pMessageFactory p2pMessageFactory;
    //private  tronMessageFactory;

    private final ByteToMessageDecoder decoder = new ByteToMessageDecoder() {
        {
            setCumulator(COMPOSITE_CUMULATOR);
        }

        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        }

        @Override
        protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        }
    };

    @Autowired
    //EthereumListener ethereumListener;

    private Args args = Args.getInstance();
    private boolean supportChunkedFrames = false;


    @Autowired
    private MessageCodec(final Args args) {
        //setMaxFramePayloadSize(config.rlpxMaxFrameSize());
    }

    private Message decodeMessage(ChannelHandlerContext ctx, ByteBuf buffer) throws IOException {

        Message msg;
        try {
            msg = createMessage(buffer.array());
        } catch (Exception ex) {
            loggerNet.debug("Incorrectly encoded message from: \t{}, dropping peer", channel);
            channel.disconnect(ReasonCode.BAD_PROTOCOL);
            return null;
        }

        if (loggerNet.isDebugEnabled())
            loggerNet.debug("From: {}    Recv:  {}", channel, msg.toString());

        //TODO: let peer know.
        //ethereumListener.onRecvMessage(channel, msg);

        channel.getNodeStatistics().rlpxInMessages.add();
        return msg;
    }

    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
        //String output = String.format("To: \t%s \tSend: \t%s", ctx.channel().remoteAddress(), msg);

        if (loggerNet.isDebugEnabled())
            loggerNet.debug("To:   {}    Send:  {}", channel, msg);

        //TODO: get data to send.
        byte[] encoded = msg.getData();
        out.add(encoded);

        channel.getNodeStatistics().rlpxOutMessages.add();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out)
        throws Exception {
        Message message = decodeMessage(ctx, msg);
            out.add(message);
    }

    private Message createMessage(byte[] data) {
        //TODO:add tron message factory here
        return p2pMessageFactory.create(data);
   }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        decoder.channelReadComplete(ctx);
        super.channelReadComplete(ctx);
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        decoder.channelInactive(ctx);
        super.channelInactive(ctx);
    }
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        decoder.handlerAdded(ctx);
        super.handlerAdded(ctx);
    }
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        decoder.handlerRemoved(ctx);
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        decoder.channelRead(ctx, msg);
    }

    public void setChannel(Channel channel){
        this.channel = channel;
    }

}