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

import java.net.HttpURLConnection

abstract class Credential {
  val username: String
  def init: Unit = { }
  def sign(c: HttpURLConnection, params:String)
}

case class BasicCredential(override val username: String, passwd:String) extends Credential {
  import com.jcraft.oaus.Util.b64encoder
  val auth = new String(b64encoder("%s:%s".format(username, passwd).getBytes("UTF-8")))

  def sign(c: HttpURLConnection, params:String) = {
    c.setRequestProperty("Authorization", "Basic "+auth)
  }
}

case class OAuthCredential(override val username: String, 
                           consumer_key: String,
                           consumer_secret: String,
                           access_token: String,
                           token_secret: String
                           ) extends Credential {
  import com.jcraft.oaus._

  var tokenCredential: TokenCredential = _
  var oac: OAuthClient = _

  override def init: Unit = {
    val clientCredential = ClientCredential(consumer_key, consumer_secret)
    tokenCredential = TokenCredential(access_token, token_secret)
    oac = new OAuthClient(clientCredential)
  }

  def sign(c: HttpURLConnection, params:String) = {
    oac.signPostRequest(c.getURL.toString, params, tokenCredential){
      (k, v) => c.setRequestProperty(k, v)
    }
  }
}
