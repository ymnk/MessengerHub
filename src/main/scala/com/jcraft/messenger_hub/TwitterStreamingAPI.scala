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

object http{
  import java.net.URLEncoder.encode
  import java.net._
  import java.net.{URL, HttpURLConnection}
  import java.io.{InputStream, IOException}
 
  private def param2str(param:(String,String)*):String =
    (for((k, v)<-param)
       yield k+"="+URLEncoder.encode(v, "UTF-8")).mkString("&")

  private def consume(in:InputStream, f:Option[String]=>Unit){
    val buf = new Array[Byte](1024)
    try{

      def loop() {
        var next = true

        if(in.available > 0) {
          in.read(buf) match{
            case -1 => next = false
            case i => f(Some(new String(buf, 0, i)));
          }
	}
        else{ 
          try{ Thread.sleep(100)}catch{case e => }
        }

        if(next) loop()
      }
      loop()

      f(None)
    }
    catch{ case e:IOException => }
    finally{ in.close }
  }

  def get(uri:String, param:(String,String)*)(f: Option[String]=>Unit)(implicit crdn:Credential) = {
    new URL(uri + "?" + param2str(param:_*)).openConnection match{
      case c:HttpURLConnection =>
        c.setRequestMethod("GET")

        crdn.sign(c, "")

        consume(c.getInputStream, f)
      case _ =>
    }
  }

  def post(uri:String, param:(String,String)*)(f: Option[String]=>Unit)(implicit crdn:Credential) ={

    new URL(uri).openConnection match{
      case c:HttpURLConnection => {
        c.setDoInput(true)
        c.setDoOutput(true)
        c.setUseCaches(false)
        c.setRequestMethod("POST")
        c.setRequestProperty("Content-Type",
                             "application/x-www-form-urlencoded")
        val content = param2str(param:_*).getBytes
        c.setRequestProperty("Content-Length", content.length.toString);

        crdn.sign(c, param2str(param:_*))

        val o = c.getOutputStream
        o.write(content)
        o.flush
        o.close

        consume(c.getInputStream, f)
      }
      case _ => None
    }
  }
}

object TwitterStreamingAPI{
  import scala.collection.mutable.{Queue, SynchronizedQueue}

  private val filter = "http://stream.twitter.com/1/statuses/filter.xml"

  // ?s will enable the DOTALL mode
  // *? is the reluctant quantifier, and not greedy.
  private val pattern_limit ="^((?s).*?)</limit>((?s).*)".r
  private val pattern_delete ="^((?s).*?)</delete>((?s).*)".r
  private val pattern_status ="^((?s).*?)</status>((?s).*)".r

  private def parseStatus(queue:Queue[Elem]) = {
    var input = ""
    val proc:Option[String]=>Unit = {
      case Some(_input) =>
        input = (input + _input) match{
          case pattern_limit(_, y) => y
          case pattern_delete(_, y) => y
          case pattern_status(x, y) =>
            queue += XML.loadString(x.trim+"</status>")
            y
          case _input => _input
        }
      case _ => 
    }
    proc
  }

  private def spawnQueueReader(f:(Elem) => Unit):Queue[Elem]={
    val queue = new SynchronizedQueue[Elem]

    import scala.concurrent.ops.spawn
    spawn{
      def loop:Unit = queue.dequeueFirst((_)=>true) match{
        case Some(e) => f(e); loop
        case _ =>
      }

      while(true){
        loop
        Thread.sleep(100)
      }
    }

    queue
  }

  def follow(id:Seq[String])(f:(Elem) => Unit)(implicit c:Credential){
    val queue = spawnQueueReader(f)
    val _id = id.take(200).mkString(",")
    http.post(filter, ("follow", _id))(parseStatus(queue))
  }

  def track(track:Seq[String])(f:(Elem) => Unit)(implicit c:Credential){
    val queue = spawnQueueReader(f)
    val _track = track.take(200).mkString(",")
    http.post(filter, ("track", _track))(parseStatus(queue))
  }
}
