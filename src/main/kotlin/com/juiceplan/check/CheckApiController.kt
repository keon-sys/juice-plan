package com.juiceplan.check

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class CheckItemRequest(val list: CheckList, val name: String, val memo: String? = null) {
    fun toInput() = CheckItemInput(list, name, memo)
}

/** 수정은 목록을 옮기지 않는다. 쇼핑에 적은 것을 준비물로 보내는 일은 만들지 않았다. */
data class CheckItemEdit(val name: String, val memo: String? = null)

data class CheckedRequest(val checked: Boolean)

/**
 * 저장·수정이 엔티티를 그대로 돌려주는 이유는 SourceController·BudgetApiController 와 같다 —
 * 클라이언트가 생성된 id 를 알아야 목록에 넣고 이후 수정·삭제를 걸 수 있다.
 *
 * 체크 토글에 전용 경로를 두는 이유는 이게 이 화면에서 압도적으로 잦은 동작이기 때문이다.
 * 체크 한 번에 이름과 메모까지 통째로 보내면, 시트를 열지도 않은 채 이름을 덮어쓰는 셈이 된다.
 */
@RestController
class CheckApiController(private val checkService: CheckService) {

    @PostMapping("/api/check/items")
    fun create(@RequestBody request: CheckItemRequest): CheckItem =
        checkService.create(request.toInput())

    @PutMapping("/api/check/items/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: CheckItemEdit): CheckItem =
        checkService.update(id, request.name, request.memo)

    @PutMapping("/api/check/items/{id}/checked")
    fun setChecked(@PathVariable id: Long, @RequestBody request: CheckedRequest): CheckItem =
        checkService.setChecked(id, request.checked)

    @DeleteMapping("/api/check/items/{id}")
    fun delete(@PathVariable id: Long) {
        checkService.delete(id)
    }
}
