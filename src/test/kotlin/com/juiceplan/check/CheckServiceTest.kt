package com.juiceplan.check

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(CheckService::class)
class CheckServiceTest {

    @Autowired lateinit var checkService: CheckService
    @Autowired lateinit var repository: CheckItemRepository

    @BeforeEach
    fun clear() {
        repository.deleteAll()
    }

    private fun input(
        name: String = "시로이코이비토",
        list: CheckList = CheckList.SHOPPING,
        memo: String? = null
    ) = CheckItemInput(list = list, name = name, memo = memo)

    @Test
    fun `create stores the item unchecked and returns it with an id`() {
        val saved = checkService.create(input())

        assertTrue(saved.id > 0)
        assertFalse(saved.checked)
        assertEquals("시로이코이비토", repository.findById(saved.id).get().name)
    }

    @Test
    fun `create trims the name`() {
        assertEquals("여권", checkService.create(input(name = "  여권  ")).name)
    }

    @Test
    fun `create rejects a blank name`() {
        // 이름 없는 체크리스트 항목은 아무 뜻이 없다 (예산 항목과 다른 점)
        assertThrows(IllegalArgumentException::class.java) { checkService.create(input(name = "   ")) }
    }

    @Test
    fun `an empty memo is stored as null`() {
        assertNull(checkService.create(input(memo = "   ")).memo)
    }

    @Test
    fun `update changes the name and memo but not the checked state`() {
        val saved = checkService.create(input())
        checkService.setChecked(saved.id, true)

        val updated = checkService.update(saved.id, "로이스 생초콜릿", "냉장 보관")

        assertEquals("로이스 생초콜릿", updated.name)
        assertEquals("냉장 보관", updated.memo)
        assertTrue(updated.checked)
    }

    @Test
    fun `update rejects a blank name`() {
        val saved = checkService.create(input())

        assertThrows(IllegalArgumentException::class.java) { checkService.update(saved.id, " ", null) }
    }

    @Test
    fun `setChecked toggles both ways`() {
        val saved = checkService.create(input())

        assertTrue(checkService.setChecked(saved.id, true).checked)
        assertFalse(checkService.setChecked(saved.id, false).checked)
    }

    @Test
    fun `delete removes the item`() {
        val saved = checkService.create(input())

        checkService.delete(saved.id)

        assertTrue(repository.findById(saved.id).isEmpty)
    }

    @Test
    fun `an unknown id is rejected on every path that needs one`() {
        assertThrows(NoSuchElementException::class.java) { checkService.update(999, "x", null) }
        assertThrows(NoSuchElementException::class.java) { checkService.setChecked(999, true) }
        assertThrows(NoSuchElementException::class.java) { checkService.delete(999) }
    }

    @Test
    fun `list keeps unchecked items first and insertion order within each group`() {
        val first = checkService.create(input(name = "첫째"))
        checkService.create(input(name = "둘째"))
        checkService.create(input(name = "셋째"))

        checkService.setChecked(first.id, true)

        assertEquals(listOf("둘째", "셋째", "첫째"), checkService.list().map { it.name })
    }

    @Test
    fun `unchecking puts an item back where it was`() {
        val first = checkService.create(input(name = "첫째"))
        checkService.create(input(name = "둘째"))

        checkService.setChecked(first.id, true)
        checkService.setChecked(first.id, false)

        // 순서를 따로 저장하지 않으므로 id 순서가 곧 원래 자리다
        assertEquals(listOf("첫째", "둘째"), checkService.list().map { it.name })
    }

    @Test
    fun `list holds every list's items so the client can split them by tab`() {
        checkService.create(input(name = "시로이코이비토", list = CheckList.SHOPPING))
        checkService.create(input(name = "여권", list = CheckList.PACKING))
        checkService.create(input(name = "환전", list = CheckList.TODO))

        assertEquals(
            listOf(CheckList.SHOPPING, CheckList.PACKING, CheckList.TODO),
            checkService.list().map { it.list }
        )
    }
}
