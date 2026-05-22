package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.AbkRuntimeStatus
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStatusJsonTest {
    @Test
    fun `ABK control runtime UI fields map into module model`() {
        val status = Gson().fromJson(
            """
            {
              "schema": 4,
              "modules": [
                {
                  "id": "meta-abk-mount",
                  "name": "ABK Meta Mount",
                  "type": "builtin",
                  "source": "abk",
                  "module_dir": "/data/adb/modules/meta-abk-mount",
                  "web_root": "/data/adb/modules/meta-abk-mount/webroot",
                  "controllable": true,
                  "enabled": true,
                  "has_web_ui": true,
                  "has_action_script": true,
                  "action_supported": true
                }
              ]
            }
            """.trimIndent(),
            AbkRuntimeStatus::class.java
        )

        val module = status.modules.single()
        assertEquals("/data/adb/modules/meta-abk-mount", module.moduleDir)
        assertEquals("/data/adb/modules/meta-abk-mount/webroot", module.webRoot)
        assertTrue(module.hasWebUi)
        assertTrue(module.hasActionScript)
        assertTrue(module.actionSupported)
    }
}
