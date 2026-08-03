package com.sentinel.bridge.feature.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FakeAccessibilityGatewayTest {

    private lateinit var gateway: FakeAccessibilityGateway

    @BeforeEach
    fun setUp() {
        gateway = FakeAccessibilityGateway()
    }

    @Test
    fun `isServiceConnected returns configured value`() {
        assertTrue(gateway.isServiceConnected())
        gateway.isConnected = false
        assertFalse(gateway.isServiceConnected())
        assertEquals(2, gateway.isServiceConnectedCallCount)
    }

    @Test
    fun `launchApp returns configured result and tracks calls`() = runTest {
        assertTrue(gateway.launchApp("com.miui.player"))
        assertEquals(1, gateway.launchAppCallCount)
        assertEquals("com.miui.player", gateway.lastLaunchAppPackage)

        gateway.launchAppResult = false
        assertFalse(gateway.launchApp("com.other.app"))
        assertEquals(2, gateway.launchAppCallCount)
        assertEquals("com.other.app", gateway.lastLaunchAppPackage)
    }

    @Test
    fun `findNodeByText returns node from map`() = runTest {
        val node = createNode(text = "Show Text")
        gateway.nodesByText["Show Text"] = node

        assertEquals(node, gateway.findNodeByText("Show Text"))
        assertNull(gateway.findNodeByText("Missing"))
        assertEquals(2, gateway.findNodeByTextCallCount)
        assertEquals("Missing", gateway.lastFindNodeByTextQuery)
    }

    @Test
    fun `findNodeByContentDescription returns node from map`() = runTest {
        val node = createNode(contentDescription = "play button")
        gateway.nodesByDescription["play button"] = node

        assertEquals(node, gateway.findNodeByContentDescription("play button"))
        assertNull(gateway.findNodeByContentDescription("unknown"))
        assertEquals(2, gateway.findNodeByContentDescriptionCallCount)
    }

    @Test
    fun `findNodeByClassName returns first node from list`() = runTest {
        val nodes = listOf(
            createNode(className = "android.widget.TextView"),
            createNode(className = "android.widget.TextView", text = "second")
        )
        gateway.nodesByClassName["android.widget.TextView"] = nodes

        assertEquals(nodes[0], gateway.findNodeByClassName("android.widget.TextView"))
        assertNull(gateway.findNodeByClassName("android.widget.Button"))
        assertEquals(2, gateway.findNodeByClassNameCallCount)
    }

    @Test
    fun `findAllNodesByClassName returns all nodes from list`() = runTest {
        val nodes = listOf(
            createNode(className = "android.widget.TextView"),
            createNode(className = "android.widget.TextView", text = "second")
        )
        gateway.nodesByClassName["android.widget.TextView"] = nodes

        assertEquals(nodes, gateway.findAllNodesByClassName("android.widget.TextView"))
        assertEquals(emptyList<AccessibilityNodeResult>(), gateway.findAllNodesByClassName("missing"))
        assertEquals(2, gateway.findAllNodesByClassNameCallCount)
    }

    @Test
    fun `findNodeByViewId returns node from map`() = runTest {
        val node = createNode(viewId = "com.miui:id/btn")
        gateway.nodesByViewId["com.miui:id/btn"] = node

        assertEquals(node, gateway.findNodeByViewId("com.miui:id/btn"))
        assertNull(gateway.findNodeByViewId("missing:id"))
        assertEquals(2, gateway.findNodeByViewIdCallCount)
    }

    @Test
    fun `clickNode returns configured result and tracks node`() = runTest {
        val node = createNode(text = "OK")
        assertTrue(gateway.clickNode(node))
        assertEquals(node, gateway.lastClickedNode)
        assertEquals(1, gateway.clickNodeCallCount)

        gateway.clickResult = false
        assertFalse(gateway.clickNode(node))
    }

    @Test
    fun `scroll methods return configured result and track calls`() = runTest {
        assertTrue(gateway.scrollForward())
        assertTrue(gateway.scrollBackward())
        assertEquals(1, gateway.scrollForwardCallCount)
        assertEquals(1, gateway.scrollBackwardCallCount)

        gateway.scrollResult = false
        assertFalse(gateway.scrollForward())
        assertFalse(gateway.scrollBackward())
    }

    @Test
    fun `getNodeChildren returns children from map`() = runTest {
        val parent = createNode(text = "parent", childCount = 2)
        val children = listOf(createNode(text = "child1"), createNode(text = "child2"))
        gateway.childrenMap[parent] = children

        assertEquals(children, gateway.getNodeChildren(parent))
        assertEquals(emptyList<AccessibilityNodeResult>(), gateway.getNodeChildren(createNode()))
        assertEquals(2, gateway.getNodeChildrenCallCount)
    }

    @Test
    fun `getRootNode returns configured root`() = runTest {
        assertNull(gateway.getRootNode())

        val root = createNode(text = "root")
        gateway.rootNode = root
        assertEquals(root, gateway.getRootNode())
        assertEquals(2, gateway.getRootNodeCallCount)
    }

    @Test
    fun `performGlobalAction returns configured result and captures action`() = runTest {
        assertTrue(gateway.performGlobalAction(1))
        assertEquals(1, gateway.lastGlobalAction)
        assertEquals(1, gateway.performGlobalActionCallCount)

        gateway.globalActionResult = false
        assertFalse(gateway.performGlobalAction(2))
        assertEquals(2, gateway.lastGlobalAction)
    }

    @Test
    fun `tapCoordinate returns configured result and captures coordinates`() = runTest {
        assertTrue(gateway.tapCoordinate(100f, 200f))
        assertEquals(100f to 200f, gateway.lastTapCoordinate)
        assertEquals(1, gateway.tapCoordinateCallCount)

        gateway.tapResult = false
        assertFalse(gateway.tapCoordinate(50f, 75f))
        assertEquals(50f to 75f, gateway.lastTapCoordinate)
    }

    @Test
    fun `reset clears all state`() = runTest {
        gateway.launchApp("com.test")
        gateway.findNodeByText("x")
        gateway.clickNode(createNode())
        gateway.isConnected = false
        gateway.launchAppResult = false

        gateway.reset()

        assertTrue(gateway.isConnected)
        assertTrue(gateway.launchAppResult)
        assertTrue(gateway.clickResult)
        assertTrue(gateway.scrollResult)
        assertTrue(gateway.tapResult)
        assertTrue(gateway.globalActionResult)
        assertTrue(gateway.nodesByText.isEmpty())
        assertNull(gateway.rootNode)
        assertEquals(0, gateway.launchAppCallCount)
        assertEquals(0, gateway.findNodeByTextCallCount)
        assertEquals(0, gateway.clickNodeCallCount)
        assertNull(gateway.lastLaunchAppPackage)
        assertNull(gateway.lastClickedNode)
    }

    private fun createNode(
        text: String? = null,
        contentDescription: String? = null,
        className: String? = null,
        viewId: String? = null,
        bounds: NodeBounds? = null,
        isClickable: Boolean = false,
        isScrollable: Boolean = false,
        childCount: Int = 0
    ) = AccessibilityNodeResult(
        text = text,
        contentDescription = contentDescription,
        className = className,
        viewId = viewId,
        bounds = bounds,
        isClickable = isClickable,
        isScrollable = isScrollable,
        childCount = childCount
    )
}
