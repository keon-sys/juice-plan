package com.juiceplan.check

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 화면이 보내는 항목 값. 엔티티와 달리 id 가 없고 이름에 앞뒤 공백이 남아 있다. */
data class CheckItemInput(
    val list: CheckList,
    val name: String,
    val memo: String? = null
)

@Service
class CheckService(private val repository: CheckItemRepository) {

    /**
     * 체크 안 한 것이 먼저, 그 안에서는 id 순.
     *
     * 순서 컬럼을 두지 않으므로 넣은 순서대로 쌓이고, 체크를 풀면 원래 자리로 저절로 돌아온다.
     * 목록을 나누지 않고 전부 돌려주는 이유는 화면이 탭을 옮길 때 서버로 다시 오지 않기 때문이다.
     */
    fun list(): List<CheckItem> =
        repository.findAll().sortedWith(compareBy({ it.checked }, { it.id }))

    @Transactional
    fun create(input: CheckItemInput): CheckItem =
        repository.save(
            CheckItem(
                list = input.list,
                name = cleanName(input.name),
                memo = cleanMemo(input.memo)
            )
        )

    @Transactional
    fun update(id: Long, name: String, memo: String?): CheckItem {
        val item = find(id)
        item.name = cleanName(name)
        item.memo = cleanMemo(memo)
        return repository.save(item)
    }

    /** 체크는 이 화면에서 가장 잦은 동작이라 이름·메모를 건드리지 않는 전용 통로를 둔다. */
    @Transactional
    fun setChecked(id: Long, checked: Boolean): CheckItem {
        val item = find(id)
        item.checked = checked
        return repository.save(item)
    }

    @Transactional
    fun delete(id: Long) {
        repository.delete(find(id))
    }

    private fun find(id: Long): CheckItem =
        repository.findById(id).orElseThrow { NoSuchElementException("없는 체크 항목입니다.") }
}

/** 이름 없는 체크 항목은 아무 뜻이 없다. 예산 항목이 빈 이름을 허용하는 것과 다르다. */
private fun cleanName(name: String): String {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty()) { "이름을 입력해주세요." }
    return trimmed
}

private fun cleanMemo(memo: String?): String? = memo?.trim()?.ifEmpty { null }
