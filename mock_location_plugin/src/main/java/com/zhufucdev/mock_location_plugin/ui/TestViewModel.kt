package com.zhufucdev.mock_location_plugin.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TestViewModel : ViewModel() {
    val serviceAvailable by lazy { MutableLiveData(TestStatus.ONGOING) }
    val developerSet by lazy { MutableLiveData(TestStatus.ONGOING) }
    val providerConnected by lazy { MutableLiveData(TestStatus.ONGOING) }
    val carrying by lazy { MutableLiveData<Boolean>() }

    fun cancel() {
        fun cancel(field: MutableLiveData<TestStatus>) {
            if (field.value == TestStatus.ONGOING) {
                field.postValue(TestStatus.UNKNOWN)
            }
        }
        cancel(serviceAvailable)
        cancel(developerSet)
        cancel(providerConnected)
        carrying.postValue(false)
    }
}