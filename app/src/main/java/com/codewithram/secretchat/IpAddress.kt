package com.codewithram.secretchat

//enum class IpAddressType(val address: String) {
//    ROUTER("192.168.0.190:4000"),
//    LAPSPOT("192.168.137.114:4000"),
//    HOTSPOT("192.168.203.231:4000"),
//    DOMAIN("social-application-backend-hwrx.onrender.com")
//}
enum class IpAddressType(val address: String) {
    ROUTER("http://192.168.0.190:4000"),
    LAPSPOT("http://192.168.137.114:4000"),
    HOTSPOT("http://192.168.203.231:4000"),
    DOMAIN("https://social-application-backend-hwrx.onrender.com")
}

object ServerConfig {
    val ipAddress = IpAddressType.DOMAIN
}



