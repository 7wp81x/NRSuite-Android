package com.swp81x.nrsuite.service;

import android.os.ParcelFileDescriptor;

interface IUsbOwnerService {
    ParcelFileDescriptor openDevice(String deviceName);
    void closeDevice(String deviceName);
}
