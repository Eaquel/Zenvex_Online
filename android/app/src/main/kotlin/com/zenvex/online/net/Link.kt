package com.zenvex.online.net

import org.json.JSONObject

/** Canlı oyun bağlantısı: Supabase Realtime kanalı ([Supa.Channel]) ya da kendi oyun sunucumuz ([GameNet]). */
interface Link {
    val joined: Boolean
    /** gidiş-dönüş süresi (ms); ölçülmediyse -1 */
    val pingMs: Int
    /** olay gönder (Supabase'de kanaldaki herkese, oyun sunucusunda olaya göre yönlendirilir) */
    fun send(event: String, payload: JSONObject)
    /** kimlik bilgisi (ad, klan, unvan, kaplama) */
    fun track(meta: JSONObject)
    fun close()
    /** bağlı değilse beklemeden yeniden bağlan */
    fun reconnectNow()
}
