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

import java.net.{URL, URLEncoder, HttpURLConnection}
import scala.xml.XML
import java.text.SimpleDateFormat
import java.util.Locale
import scala.actors.Actor
import scala.actors.Actor.loop
import scala.concurrent.ops.spawn
import scala.concurrent.SyncChannel
import scala.collection.mutable.Queue

class TwitterConnector(credential: Credential) extends Connector {

  var tweet_interval = 30 * 1000

  abstract class MESSAGE
  case object GETTIMELINE extends MESSAGE
  case object TICK extends MESSAGE

  val channel = new Queue[String]
  var cache:Option[String] = None
  var hashtag: Option[String] = None

  private def append: String = 
    hashtag.map(h => if(h.startsWith(" ")) h else " "+h).getOrElse("")

  def write(text:String) {
    def chop(text:String): String = {
      text match {
        case e if e.length>140 => chop(text.substring(0, text.length-1))
        case e => URLEncoder.encode(e, "UTF-8")
      }
    }
    channel += chop(short(text) + append)
  }

  private val pattern = "(http://[a-zA-Z0-9%+\\-&=?#./$()!\"$'^~@]+)".r

  private def tinyURL(s:String) = if(s.startsWith("http://tinyurl")) s else
    io.Source.fromURL(new URL("http://tinyurl.com/api-create.php?url="+s)).mkString("")

  private def replace(s:String, o:String, n:String) = 
    s.substring(0, s.indexOf(o))+n+s.substring(s.indexOf(o)+o.length)

  private def short(s:String): String = {
    if(s.length < (140 - append.length)) return s
    var source = s
    try{
      def _short(s:String):String = pattern.findFirstMatchIn(s) map {m =>
        m.before + tinyURL(m.matched) + _short(m.after.toString)
      } getOrElse s
      source = _short(s)
    }
    catch{ case e => println(e) }

    source
  }

  private def post: Boolean = {

    if(cache.isEmpty){
      if(!channel.isEmpty){
        cache = Some(channel.dequeue)
      }
    }

    if(!cache.isEmpty){
      val tweet = cache.get 
      val url = "http://twitter.com/statuses/update.xml"
      val urlConn = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
      urlConn.setRequestMethod("POST")
      urlConn.setDoOutput(true);

      credential.sign(urlConn, "status="+tweet)

      val writer = new java.io.PrintWriter(urlConn.getOutputStream)
      writer.print("status="+tweet)
      writer.close()
      urlConn.connect()

      if(urlConn.getResponseCode == 200){
        cache=None
        true
      } 
      else {
        false
      }
    }
    else
      false
  }

  protected class Poster extends Actor{
    def act {
      this ! TICK 
      loop {
        react {
          case TICK => {
            if(post){
              ping(this, tweet_interval, TICK)
            }
            else{
              ping(this, 1000, TICK)
            }
          }
        }
      }
    }
  }
 
  private class FriendsTimeline(credential: Credential) extends Actor{
    val username = credential.username

    val url = "http://twitter.com/statuses/replies.xml"

    val df = new SimpleDateFormat("EEE MMM dd HH:mm:ss +0000 yyyy", Locale.US)

    var lastTime:Long = -1
    val interval = 300 * 1000

    def act {
      this ! GETTIMELINE
      loop {
        react {
          case GETTIMELINE => {
            try{ lastTime = timeline(lastTime) }
            catch{case e:java.io.IOException => }
            ping(this, interval, GETTIMELINE)
          }
        }
      }
    }

    private def timeline(_lastTime:Long)={
      var lastTime=_lastTime
      val urlConn = new URL(url).openConnection.asInstanceOf[HttpURLConnection]

      credential.sign(urlConn, "")

      val track = hashtag getOrElse ("@"+username)

      urlConn.connect();
      urlConn.getResponseCode
      for (s <- XML.load(urlConn.getInputStream) \ "status" reverse ;
           created_at = s \ "created_at" text ;
           time = df.parse(created_at).getTime if(time>lastTime)) {
           lastTime=time
           var (text, user_name, screen_name) =
              (s \ "text" text,
               s \ "user" \ "name" text,
               s \ "user" \ "screen_name" text)
           if(username != screen_name && text.indexOf(track) != -1){
             if(_lastTime != -1){  // At first time, we will skip it.
               println(created_at+" ["+user_name+"] "+
                       text.substring(("@"+username).length).trim)
               writeToOthers(user_name+": "+
                             text.substring(("@"+username).length).trim)
             }
           }
      }
      lastTime
    }
  }

  private def ping(a:Actor, sleep:Long, m:MESSAGE) {
      spawn { Thread.sleep(sleep); a ! m }
  }

  override def start = {
    credential.init
    new FriendsTimeline(credential).start
    new Poster().start
  }
}
