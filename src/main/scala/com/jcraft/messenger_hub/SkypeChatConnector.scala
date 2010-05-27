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

/**
 * Skype4Java[1] is required to be compiled.
 * [1] https://developer.skype.com/wiki/Java_API
 */

import com.skype.{Skype, Chat, ChatMessage, ChatMessageAdapter} 

class SkypeChatConnector(chatId:String) extends Connector{

  Skype.setDeamon(false)

  val Array(chat) = Skype.getAllChats.filter(_.getId.toString==chatId)

  var lastSkypeWriteTime:Long = -1

  def write(message:String){
    lastSkypeWriteTime=System.currentTimeMillis
    chat.send(message)
  }

  val interval:Long = 3*1000
  
  Skype.addChatMessageListener(
    new ChatMessageAdapter {
      override def chatMessageReceived(m:ChatMessage) = chatMessage(m)
      override def chatMessageSent(m:ChatMessage) = {
        if(System.currentTimeMillis - lastSkypeWriteTime > interval){
          //chatMessage(m)
        }
      }
      private def chatMessage(m:ChatMessage){
        val c=m.getChat
        if(chat==c){
          val sender = m.getSender
          val senderId = m.getSenderId
          val senderDisplayName = m.getSenderDisplayName
          m.getType.toString match {
            case "SAID" => {
              val content = m.getContent
              writeToOthers(senderDisplayName+": "+content)
            }
            case "ADDEDMEMBERS" => {
              writeToOthers(senderDisplayName+" joined this chat.")
            }
            case "LEFT" => {
              writeToOthers(senderDisplayName+" left this chat.")
            }
            case _ =>
          }
        }
      }
    }
  )
}

object ChatList {
  def main(arg:Array[String]){
    Skype.getAllChats.foreach { c =>
      import c._
      println("Id: "+getId+" , Title: "+getWindowTitle)
    }
  }
}
