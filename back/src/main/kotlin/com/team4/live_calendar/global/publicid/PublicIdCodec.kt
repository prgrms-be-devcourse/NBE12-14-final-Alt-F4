package com.team4.live_calendar.global.publicid

import org.sqids.Sqids

// 숫자 PK <-> URL용 코드. Crockford Base32 32종류 글자를 섞은 알파벳으로 Sqids 인코딩한다
// 암호화가 아니다. 알파벳을 알면 누구나 풀 수 있으므로 접근은 여전히 참여자 확인(404)이 막는다
class PublicIdCodec(alphabet: String, private val minLength: Int) {

    init {
        // 알파벳이 틀린 채로 뜨면 나중에 코드를 못 읽는다. 시작할 때 바로 실패시킨다
        require(alphabet.length == CROCKFORD.length && alphabet.toSet() == CROCKFORD.toSet()) {
            "알파벳은 Crockford Base32 32종류 글자를 한 번씩 섞은 값이어야 합니다."
        }
        require(minLength > 0) { "최소 길이는 1 이상이어야 합니다." }
    }

    private val sqids: Sqids = Sqids.builder()
        .alphabet(alphabet)
        .minLength(minLength)
        .build()

    fun encode(id: Long): String = sqids.encode(listOf(id))

    // 못 읽는 코드는 null. 부르는 쪽이 자기 리소스의 404로 바꾼다
    fun decode(code: String): Long? {
        val normalized = normalize(code)
        if (normalized.length < minLength) return null
        // 아주 긴 문자열은 Long 범위를 넘어 예외가 날 수 있어서 못 읽는 코드로 본다
        val ids = runCatching { sqids.decode(normalized) }.getOrNull() ?: return null
        if (ids.size != 1) return null
        // 같은 숫자로 읽히는 다른 문자열이 있다. 다시 만들어서 같을 때만 인정한다 (Sqids 공식 권장)
        return ids[0].takeIf { encode(it) == normalized }
    }

    companion object {
        // 헷갈리는 I·L·O·U를 뺀 숫자 10자 + 영문 대문자 22자
        const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        // Crockford 규격의 입력 규칙: 소문자도 받고, I·L은 1로, O는 0으로 읽는다
        fun normalize(code: String): String =
            code.trim().uppercase()
                .replace('I', '1')
                .replace('L', '1')
                .replace('O', '0')
    }
}
