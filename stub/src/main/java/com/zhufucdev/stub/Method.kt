package com.zhufucdev.stub

enum class Method(val directHook: Boolean, val testProviderTrick: Boolean) {
    XPOSED_ONLY(true, false), HYBRID(false, true), TEST_PROVIDER_ONLY(false, false);

    val involveXposed: Boolean
        get() = directHook || testProviderTrick
}