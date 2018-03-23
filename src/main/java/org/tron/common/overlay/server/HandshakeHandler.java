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
package org.tron.common.overlay.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.discover.Node;
import org.tron.common.overlay.discover.NodeManager;
import org.tron.common.overlay.message.HelloMessage;
import org.tron.common.overlay.message.P2pMessage;
import org.tron.common.overlay.message.P2pMessageFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.tron.common.overlay.message.StaticMessages.PING_MESSAGE;
import static org.tron.common.overlay.message.StaticMessages.PONG_MESSAGE;

/**
 * The Netty handler which manages initial negotiation with peer (when either we initiating
 * connection or remote peer initiates)
 *
 * The initial handshake includes: - first AuthInitiate -> AuthResponse messages when peers exchange
 * with secrets - second P2P Hello messages when P2P protocol and subprotocol capabilities are
 * negotiated
 *
 * After the handshake is done this handler reports secrets and other data to the Channel which
 * installs further handlers depending on the protocol parameters. This handler is finally removed
 * from the pipeline.
 */
@Component
@Scope("prototype")
public class HandshakeHandler extends ByteToMessageDecoder {

  private static final Logger logger = LoggerFactory.getLogger("HandshakeHandler");

  private byte[] remoteId;
  private Channel channel;
  private boolean isInitiator = false;
  private static ScheduledExecutorService pingTimer =
          Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "P2pPingTimer"));
  private ScheduledFuture<?> pingTask;

  @Autowired
  private MessageQueue msgQueue;

  @Autowired
  private NodeManager nodeManager;

//  @Autowired
//  public HandshakeHandler(final NodeManager nodeManager) throws  Exception{
//    this.nodeManager = nodeManager;
//  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    channel.setInetSocketAddress((InetSocketAddress) ctx.channel().remoteAddress());
    if (remoteId.length == 64) {
      channel.initWithNode(remoteId);
      //channel.getNodeStatistics().rlpxAuthMessagesSent.add();
      channel.sendHelloMessage(ctx, Hex.toHexString(nodeManager.getPublicHomeNode().getId()));
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
    byte[] encoded = new byte[buffer.readableBytes()];
    buffer.readBytes(encoded);
    P2pMessage msg = P2pMessageFactory.create(encoded);
    handleMsg(ctx, msg);
    channel.activateTron(ctx);
  }

  // consume handshake, producing no resulting message to upper layers
  private void handleMsg(final ChannelHandlerContext ctx, P2pMessage msg) throws Exception {

    logger.info("rcv msg from [{}] type = {}, len = {}", msg.getCommand(), msg.getData().length, ctx.channel().remoteAddress());

    switch (msg.getCommand()) {
      case HELLO:
        //msgQueue.receivedMessage(msg);
        handleHelloMsg((HelloMessage) msg, ctx);
        break;
      case DISCONNECT:
        //msgQueue.receivedMessage(msg);
        //channel.getNodeStatistics().nodeDisconnectedRemote(ReasonCode.fromInt(((DisconnectMessage) msg).getReason()));
        processDisconnect(ctx);
        break;
      case PING:
        //msgQueue.receivedMessage(msg);
        ctx.writeAndFlush(PONG_MESSAGE);
        break;
      case PONG:
        //msgQueue.receivedMessage(msg);
        //channel.getNodeStatistics().lastPongReplyTime.set(System.currentTimeMillis());
        break;
      default:
        logger.info("rcv data msg.");
        //ctx.fireChannelRead(msg);
        break;
    }
  }

  public void handleHelloMsg(HelloMessage msg, ChannelHandlerContext ctx) throws Exception{
    if (remoteId.length != 64 && !isInitiator) {
      logger.info("get msg node id , {}", msg.getPeerId());
      channel.initWithNode(Hex.decode(msg.getPeerId()), msg.getListenPort());
      channel.getNodeStatistics().rlpxAuthMessagesSent.add();
      channel.sendHelloMessage(ctx, Hex.toHexString(nodeManager.getPublicHomeNode().getId()));
      nodeManager.getNodeHandler(new Node(Hex.decode(msg.getPeerId()), channel.getInetSocketAddress().getHostString(), msg.getListenPort()));
    }
    isInitiator = true;
    //msgQueue.activate(ctx);
    //startTimers();
  }

  public void setRemoteId(String remoteId, Channel channel) {
    this.remoteId = Hex.decode(remoteId);
    this.channel = channel;
  }

  public byte[] getRemoteId() {
    return remoteId;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    if (channel.isDiscoveryMode()) {
      logger.trace("Handshake failed: " + cause);
    } else {
      if (cause instanceof IOException || cause instanceof ReadTimeoutException) {
        logger.debug("Handshake failed: " + ctx.channel().remoteAddress() + ": " + cause);
      } else {
        logger.warn("Handshake failed: ", cause);
      }
    }
    ctx.close();
  }

  private void processDisconnect(ChannelHandlerContext ctx) {
    logger.info("disconnect channel {}", channel);
    ctx.close();
    pingTask.cancel(false);
  }

  private void startTimers() {

    pingTask = pingTimer.scheduleAtFixedRate(() -> {
      try {

        logger.info("ping msg to queue, {}", PING_MESSAGE);
        msgQueue.sendMessage(PING_MESSAGE);

       // channel .writeAndFlush(Unpooled.wrappedBuffer(StaticMessages.PING_MESSAGE.getSendData())).sync();

      } catch (Throwable t) {
        logger.error("Unhandled exception", t);
      }
    }, 2, 10, TimeUnit.SECONDS);
  }
}
