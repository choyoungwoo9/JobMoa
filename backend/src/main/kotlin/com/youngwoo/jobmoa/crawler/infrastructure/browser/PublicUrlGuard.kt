package com.youngwoo.jobmoa.crawler.infrastructure.browser

import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI

/** Rejects local-network targets for on-demand public-page browsing, including redirects and subresources. */
object PublicUrlGuard {
    fun validate(value: String) {
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("공개 HTTPS 주소가 아닙니다.") }
        val host = uri.host?.lowercase()?.trimEnd('.')
            ?: throw IllegalArgumentException("공개 HTTPS 주소에 호스트가 없습니다.")
        require(uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null && (uri.port == -1 || uri.port == 443)) {
            "HTTPS 공개 주소만 열 수 있습니다."
        }
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal")) {
            "로컬 네트워크 주소는 열 수 없습니다."
        }
        val addresses = try { InetAddress.getAllByName(host) } catch (_: Exception) {
            throw IllegalArgumentException("주소의 공개 IP를 확인할 수 없습니다.")
        }
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "로컬 또는 사설 네트워크 주소는 열 수 없습니다." }
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        if (address is Inet4Address) {
            val b = address.address.map { it.toInt() and 0xff }
            val first = b[0]; val second = b[1]
            if (first == 0 || first == 10 || first == 127 || first >= 224) return false
            if (first == 100 && second in 64..127) return false // carrier-grade NAT
            if (first == 169 && second == 254) return false
            if (first == 172 && second in 16..31) return false
            if (first == 192 && second == 168) return false
            if (first == 192 && second == 0 && b[2] == 0) return false
            if (first == 192 && second == 0 && b[2] == 2) return false
            if (first == 192 && second == 88 && b[2] == 99) return false
            if (first == 198 && second in 18..19) return false // benchmarking networks
            if (first == 198 && second == 51 && b[2] == 100) return false
            if (first == 203 && second == 0 && b[2] == 113) return false
        } else {
            val b = address.address.map { it.toInt() and 0xff }
            if ((b[0] and 0xe0) != 0x20) return false // only global unicast 2000::/3
            if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8) return false // documentation prefix
        }
        return true
    }
}
