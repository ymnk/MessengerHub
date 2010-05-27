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

import java.net.Socket
import java.io._
import scala.concurrent.ops.spawn

class IRCConnector(host:String, port:Int,
                   username:String, passwd:String, 
                   channel:String) extends Connector{

  private val crlf=Array(0x0d, 0x0a).map(_.asInstanceOf[Byte])
  private var sock:Socket = null

  private var in:BufferedReader = null
  private var out:OutputStream = null

  private def connect(){
    sock =  new Socket(host, port)
    in = new BufferedReader(new InputStreamReader(sock.getInputStream, "UTF-8"))
    out = sock.getOutputStream
  }

  connect()

  def write(text:String){
    writeRaw("PRIVMSG "+channel+" :"+text)
  }

  private def writeRaw(msg:String){

    // if(!msg.startsWith("PONG")){ println("writeRaw: "+msg) }

    val b = try{ 
      msg.getBytes("UTF-8") 
    }
    catch{ 
      case e:UnsupportedEncodingException => msg.getBytes 
    }
 
    try{
      out.write(b)
      out.write(crlf)
      out.flush
    }
    catch{
      case e =>
        println("failed to write to IRC: "+e)
        try{ Thread.sleep(1000)}catch{case ee => }
        connect()
        out.write(b)
        out.write(crlf)
        out.flush
    }
  }

  def parseCommand(msg:String) = msg.split(" ", 0) match { 
    case Array(pr, c, p@_*) if pr.startsWith(":") => 
      (Some(pr.substring(1)), c.toUpperCase, p.mkString(" "))
    case Array(c, p@_*)  => (None, c.toUpperCase, p.mkString(" "))
  }

  def parsePRIVMSG(msg:String) = msg.split(" ", 0) match {
    case Array(n) => (n, "")
    case Array(n, m@_*) if m(0).startsWith(":") =>
      (n, m.mkString(" ").substring(1))
    case Array(n, m@_*) => (n, m.mkString(" "))
  }

  spawn{
    def loop{
      if(in.ready){
        val msg=in.readLine
        parseCommand(msg) match {
          case (pr, "PRIVMSG", params) =>
            val who = pr map(_.split("!")(0)) getOrElse "unknown"
            println("PRIVMSG: "+who+" "+params)
            val(nick, msg) = parsePRIVMSG(params)
            if(nick==channel && msg!=""){
              writeToOthers(who+": "+msg)
            }
          case (pr, "PING", params) =>
            writeRaw("PONG "+params)
          case (a, b, c) => 
            val message = (a getOrElse "") + b + c
            println("IRCConnector: "+message)
        } 
      }
      else{
        try{ Thread.sleep(100)}catch{case e=> }
      }
      loop
    }
    loop
  }

  writeRaw("PASS "+passwd)
  writeRaw("NICK "+username) 
  writeRaw("USER "+username+" 127.0.0.1 * :"+username) 
  writeRaw("JOIN "+channel)
}
