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

import java.net.{Authenticator, PasswordAuthentication}
import java.net.{URL, URLEncoder, HttpURLConnection}
import scala.xml.{XML, Elem, Node}
import java.text.SimpleDateFormat
import scala.collection.mutable.Map
import java.util.Locale
import scala.actors.Actor
import scala.actors.Actor.loop
import scala.concurrent.ops.spawn

class TwitterConnectorStreaming(credential:Credential) 
   extends TwitterConnector(credential) {

  override def start = {

    tweet_interval = tweet_interval / 2

    credential.init

    new Poster().start

    val track = hashtag getOrElse ("@"+credential.username)

    spawn{
      implicit val c = credential
      TwitterStreamingAPIJSON.track(List(track)) { case s =>
        var (text, user_name, screen_name) =
          (s \ "text" text,
           s \ "user" \ "name" text,
           s \ "user" \ "screen_name" text)
        if(credential.username!=screen_name){
          println(" ["+user_name+"] "+text.trim)
          writeToOthers(user_name+": "+text.trim)
        }
      }
    }
  }
}
