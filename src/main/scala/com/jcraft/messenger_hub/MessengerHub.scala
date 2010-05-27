/*
Copyright (c) 2010 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

   1. Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.

   2. Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in
      the documentation and/or other materials provided with the distribution.

   3. The names of the authors may not be used to endorse or promote products
      derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
 INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.jcraft.messenger_hub

import scala.collection.mutable.{Set, Map}

class Hub {
  private val lastMessage = Map.empty[Connector, String]
  private val connectors = Set.empty[Connector]

  def += (c:Connector) = synchronized { 
    c.hub = this
    c.start 
    connectors += c
    lastMessage += (c -> "") 
  } 

  def write(ignore:Connector, message:String){
    lastMessage.get(ignore) match{
      case Some(`message`) => 
      case _ => {
        lastMessage += (ignore -> message)
        connectors.foreach{ c => if(c!=ignore) c.write(message) }
      }
    }
  }
}

object MessengerHub {
  def main(arg: Array[String]): Unit = {
    import net.lag.configgy.Configgy
    import net.lag.logging.Logger

    Configgy.configure(arg(0))
    val config = Configgy.config

    val hub = new Hub

    for { 
      m <- config.getConfigMap("skype")
      chat_id <-  m.getString("chat_id")
    }{
      hub += new SkypeChatConnector(chat_id)
    }

    for {
      username <- config.getString("twitter.username")
      streaming <- config.getBool("twitter.streaming") orElse Some(false)
    }{
      val credential: Option[Credential] = {
        for{ m <- config.getConfigMap("twitter.basic-auth")
               password <- m.getString("password") }
            yield BasicCredential(username, password)
        } orElse {
          for { m <- config.getConfigMap("twitter.oauth-auth")
                consumer_key <- m.getString("consumer_key")
                consumer_secret <- m.getString("consumer_secret")
                access_token <- m.getString("access_token")
                token_secret <- m.getString("token_secret")}
            yield OAuthCredential(username,
                                  consumer_key, consumer_secret,
                                  access_token, token_secret)
        } orElse None
  
      credential foreach { c =>
        val connector = 
          if(streaming) new TwitterConnectorStreaming(c) else new TwitterConnector(c)
        connector.hashtag = config.getString("twitter.hashtag")
        hub += connector
      }
    }

    for {
      m <- config.getConfigMap("irc")
      host <- m.getString("hostname")
      port <- m.getInt("port") orElse Some(6667)
      username <- m.getString("username")
      password <- m.getString("password")
      channel <- m.getString("channel") 
    } {
      hub += new IRCConnector(host, port, username, password, channel)
    }
  }
}

