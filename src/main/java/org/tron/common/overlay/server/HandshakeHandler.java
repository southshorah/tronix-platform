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
import org.tron.common.crypto.ECKey;
import org.tron.common.overlay.discover.NodeManager;
import org.tron.common.overlay.message.*;
import org.tron.core.config.args.Args;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
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
  private boolean isHandshakeDone;
  private boolean isInitiator = false;

  @Autowired
  private MessageQueue msgQueue;

  @Autowired
  private final NodeManager nodeManager;

  @Autowired
  public HandshakeHandler(final NodeManager nodeManager) {
    this.nodeManager = nodeManager;
  }
  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    channel.setInetSocketAddress((InetSocketAddress) ctx.channel().remoteAddress());
    if (remoteId.length == 64) {
      channel.initWithNode(remoteId);
      channel.getNodeStatistics().rlpxAuthMessagesSent.add();
      channel.sendHelloMessage(ctx, Hex.toHexString(nodeManager.getPublicHomeNode().getId()));
      isInitiator = true;
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
    byte[] encoded = new byte[buffer.readableBytes()];
    buffer.readBytes(encoded);
    P2pMessage msg = P2pMessageFactory.create(encoded);
    handleMsg(ctx, msg);
  }

  // consume handshake, producing no resulting message to upper layers
  private void handleMsg(final ChannelHandlerContext ctx, P2pMessage msg) throws Exception {

    switch (msg.getCommand()) {
      case HELLO:
        msgQueue.receivedMessage(msg);
        setHandshake((HelloMessage) msg, ctx);
        break;
      case DISCONNECT:
        msgQueue.receivedMessage(msg);
        channel.getNodeStatistics()
                .nodeDisconnectedRemote(ReasonCode.fromInt(((DisconnectMessage) msg).getReason()));
        processDisconnect(ctx, (DisconnectMessage) msg);
        break;
      case PING:
        msgQueue.receivedMessage(msg);
        ctx.writeAndFlush(PONG_MESSAGE);
        break;
      case PONG:
        msgQueue.receivedMessage(msg);
        channel.getNodeStatistics().lastPongReplyTime.set(System.currentTimeMillis());
        break;
      default:
        ctx.fireChannelRead(msg);
        break;
    }
  }

  public void handleHelloMsg(HelloMessage msg) {
    if (isInitiator) {
      //todo send disconnection msg
    }
    if (isInitiator) {
      channel.initWithNode(Hex.decode(msg.getPeerId()));
      channel.getNodeStatistics().rlpxAuthMessagesSent.add();
      channel.sendHelloMessage(ctx, Hex.toHexString(nodeManager.getPublicHomeNode().getId()));
      isInitiator = true;
    }

  }

    if (isInitiator) {
      loggerWire.debug("initiator");

      if (msg instanceof HelloMessage) {
        isHandshakeDone = true;
        this.channel.publicHandshakeFinished(ctx, (HelloMessage) msg);
      } else {
        channel.getNodeStatistics()
            .nodeDisconnectedRemote(ReasonCode.fromInt(((DisconnectMessage) msg).getReason()));
      }

    } else {
      loggerWire.debug("Not initiator.");

      if (msg instanceof DisconnectMessage) {
        loggerNet.debug("Active remote peer disconnected right after handshake.");
        return;
      }

      if (!(msg instanceof HelloMessage)) {
        throw new RuntimeException("The message type is not HELLO or DISCONNECT: " + msg);
      }

      final HelloMessage inboundHelloMessage = (HelloMessage) msg;
      // now we know both remote nodeId and port
      // let's set node, that will cause registering node in NodeManager
      channel.initWithNode(remoteId, inboundHelloMessage.getListenPort());
      channel.sendHelloMessage(ctx, Hex.toHexString(nodeId));
      isHandshakeDone = true;
      this.channel.publicHandshakeFinished(ctx, inboundHelloMessage);
      channel.getNodeStatistics().rlpxInHello.add();
    }
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
      loggerNet.trace("Handshake failed: " + cause);
    } else {
      if (cause instanceof IOException || cause instanceof ReadTimeoutException) {
        loggerNet.debug("Handshake failed: " + ctx.channel().remoteAddress() + ": " + cause);
      } else {
        loggerNet.warn("Handshake failed: ", cause);
      }
    }
    ctx.close();
  }

  private void processDisconnect(ChannelHandlerContext ctx, DisconnectMessage msg) {

    if (logger.isInfoEnabled() && ReasonCode.fromInt(msg.getReason()) == ReasonCode.USELESS_PEER) {

      if (channel.getNodeStatistics().ethInbound.get() - ethInbound > 1 ||
              channel.getNodeStatistics().ethOutbound.get() - ethOutbound > 1) {

        // it means that we've been disconnected
        // after some incorrect action from our peer
        // need to log this moment
        logger.debug("From: \t{}\t [DISCONNECT reason=BAD_PEER_ACTION]", channel);
      }
    }
    ctx.close();
    killTimers();
  }


  public void setHandshake(HelloMessage msg, ChannelHandlerContext ctx) {

    channel.getNodeStatistics().setClientId(msg.getClientId());
//        channel.getNodeStatistics().capabilities.clear();
//        channel.getNodeStatistics().capabilities.addAll(msg.getCapabilities());

    this.ethInbound = (int) channel.getNodeStatistics().ethInbound.get();
    this.ethOutbound = (int) channel.getNodeStatistics().ethOutbound.get();

//        this.handshakeHelloMessage = msg;

//        List<Capability> capInCommon = getSupportedCapabilities(msg);
//        channel.initMessageCodes(capInCommon);

    channel.activateTron(ctx);

    //todo: init peer's block status and sync
    //tronListener.onHandShakePeer(channel, msg);
  }

  /**
   * submit transaction to the network
   */

  public void sendDisconnect() {
    msgQueue.disconnect();
  }

  private void startTimers() {

    logger.info(args.getNodeP2pPingInterval() + "");
    logger.info(args.getNodeP2pPingInterval() + "");
    // sample for pinging in background
    pingTask = pingTimer.scheduleAtFixedRate(() -> {
      try {
        msgQueue.sendMessage(PING_MESSAGE);
      } catch (Throwable t) {
        logger.error("Unhandled exception", t);
      }
    }, 2, 10, TimeUnit.SECONDS);
  }
}
