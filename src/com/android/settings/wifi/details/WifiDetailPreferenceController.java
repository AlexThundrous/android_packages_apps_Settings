/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings.wifi.details;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkBadging;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settings.R;
import com.android.settings.core.PreferenceController;
import com.android.settings.core.lifecycle.Lifecycle;
import com.android.settings.core.lifecycle.LifecycleObserver;
import com.android.settings.core.lifecycle.events.OnResume;
import com.android.settings.wifi.WifiDetailPreference;
import com.android.settingslib.wifi.AccessPoint;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.StringJoiner;

/**
 * Controller for logic pertaining to displaying Wifi information for the
 * {@link WifiNetworkDetailsFragment}.
 */
public class WifiDetailPreferenceController extends PreferenceController implements
        LifecycleObserver, OnResume {
    private static final String TAG = "WifiDetailsPrefCtrl";

    @VisibleForTesting
    static final String KEY_CONNECTION_DETAIL_PREF = "connection_detail";
    @VisibleForTesting
    static final String KEY_SIGNAL_STRENGTH_PREF = "signal_strength";
    @VisibleForTesting
    static final String KEY_LINK_SPEED = "link_speed";
    @VisibleForTesting
    static final String KEY_FREQUENCY_PREF = "frequency";
    @VisibleForTesting
    static final String KEY_SECURITY_PREF = "security";
    @VisibleForTesting
    static final String KEY_MAC_ADDRESS_PREF = "mac_address";
    @VisibleForTesting
    static final String KEY_IP_ADDRESS_PREF = "ip_address";
    @VisibleForTesting
    static final String KEY_GATEWAY_PREF = "gateway";
    @VisibleForTesting
    static final String KEY_SUBNET_MASK_PREF = "subnet_mask";
    @VisibleForTesting
    static final String KEY_DNS_PREF = "dns";
    @VisibleForTesting
    static final String KEY_IPV6_ADDRESS_CATEGORY = "ipv6_details_category";

    private AccessPoint mAccessPoint;
    private NetworkInfo mNetworkInfo;
    private Context mPrefContext;
    private int mRssi;
    private String[] mSignalStr;
    private WifiConfiguration mWifiConfig;
    private WifiInfo mWifiInfo;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;

    // Preferences - in order of appearance
    private Preference mConnectionDetailPref;
    private WifiDetailPreference mSignalStrengthPref;
    private WifiDetailPreference mLinkSpeedPref;
    private WifiDetailPreference mFrequencyPref;
    private WifiDetailPreference mSecurityPref;
    private WifiDetailPreference mMacAddressPref;
    private WifiDetailPreference mIpAddressPref;
    private WifiDetailPreference mGatewayPref;
    private WifiDetailPreference mSubnetPref;
    private WifiDetailPreference mDnsPref;
    private PreferenceCategory mIpv6AddressCategory;

    public WifiDetailPreferenceController(AccessPoint accessPoint, Context context,
            Lifecycle lifecycle, WifiManager wifiManager, ConnectivityManager connectivityManager) {
        super(context);

        mAccessPoint = accessPoint;
        mNetworkInfo = accessPoint.getNetworkInfo();
        mRssi = accessPoint.getRssi();
        mSignalStr = context.getResources().getStringArray(R.array.wifi_signal);
        mWifiConfig = accessPoint.getConfig();
        mWifiManager = wifiManager;
        mConnectivityManager = connectivityManager;
        mWifiInfo = wifiManager.getConnectionInfo();

        lifecycle.addObserver(this);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        // Returns null since this controller contains more than one Preference
        return null;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        mPrefContext = screen.getPreferenceManager().getContext();

        mConnectionDetailPref = screen.findPreference(KEY_CONNECTION_DETAIL_PREF);

        mSignalStrengthPref =
                (WifiDetailPreference) screen.findPreference(KEY_SIGNAL_STRENGTH_PREF);
        mLinkSpeedPref = (WifiDetailPreference) screen.findPreference(KEY_LINK_SPEED);
        mFrequencyPref = (WifiDetailPreference) screen.findPreference(KEY_FREQUENCY_PREF);
        mSecurityPref = (WifiDetailPreference) screen.findPreference(KEY_SECURITY_PREF);

        mMacAddressPref = (WifiDetailPreference) screen.findPreference(KEY_MAC_ADDRESS_PREF);
        mIpAddressPref = (WifiDetailPreference) screen.findPreference(KEY_IP_ADDRESS_PREF);
        mGatewayPref = (WifiDetailPreference) screen.findPreference(KEY_GATEWAY_PREF);
        mSubnetPref = (WifiDetailPreference) screen.findPreference(KEY_SUBNET_MASK_PREF);
        mDnsPref = (WifiDetailPreference) screen.findPreference(KEY_DNS_PREF);

        mIpv6AddressCategory =
                (PreferenceCategory) screen.findPreference(KEY_IPV6_ADDRESS_CATEGORY);

        mSecurityPref.setDetailText(mAccessPoint.getSecurityString(false /* concise */));
    }

    public WifiInfo getWifiInfo() {
        return mWifiInfo;
    }

    @Override
    public void onResume() {
        mWifiInfo = mWifiManager.getConnectionInfo();

        refreshFromWifiInfo();
        setIpText();
    }

    private void refreshFromWifiInfo() {
        if (mWifiInfo == null) {
            return;
        }
        mAccessPoint.update(mWifiConfig, mWifiInfo, mNetworkInfo);

        int iconSignalLevel = WifiManager.calculateSignalLevel(
                mWifiInfo.getRssi(), WifiManager.RSSI_LEVELS);
        Drawable wifiIcon = NetworkBadging.getWifiIcon(
                iconSignalLevel, NetworkBadging.BADGING_NONE, mContext.getTheme()).mutate();

        // Connected Header Pref
        mConnectionDetailPref.setIcon(wifiIcon);
        mConnectionDetailPref.setTitle(mAccessPoint.getSettingsSummary());

        // MAC Address Pref
        mMacAddressPref.setDetailText(mWifiInfo.getMacAddress());

        // Signal Strength Pref
        Drawable wifiIconDark = wifiIcon.getConstantState().newDrawable().mutate();
        wifiIconDark.setTint(mContext.getResources().getColor(
                R.color.wifi_details_icon_color, mContext.getTheme()));
        mSignalStrengthPref.setIcon(wifiIconDark);

        int summarySignalLevel = mAccessPoint.getLevel();
        mSignalStrengthPref.setDetailText(mSignalStr[summarySignalLevel]);

        // Link Speed Pref
        int linkSpeedMbps = mWifiInfo.getLinkSpeed();
        mLinkSpeedPref.setVisible(linkSpeedMbps >= 0);
        mLinkSpeedPref.setDetailText(mContext.getString(
                R.string.link_speed, mWifiInfo.getLinkSpeed()));

        // Frequency Pref
        final int frequency = mWifiInfo.getFrequency();
        String band = null;
        if (frequency >= AccessPoint.LOWER_FREQ_24GHZ
                && frequency < AccessPoint.HIGHER_FREQ_24GHZ) {
            band = mContext.getResources().getString(R.string.wifi_band_24ghz);
        } else if (frequency >= AccessPoint.LOWER_FREQ_5GHZ
                && frequency < AccessPoint.HIGHER_FREQ_5GHZ) {
            band = mContext.getResources().getString(R.string.wifi_band_5ghz);
        } else {
            Log.e(TAG, "Unexpected frequency " + frequency);
        }
        mFrequencyPref.setDetailText(band);
    }

    private void setIpText() {
        // Reset all fields
        mIpv6AddressCategory.removeAll();
        mIpv6AddressCategory.setVisible(false);
        mIpAddressPref.setVisible(false);
        mSubnetPref.setVisible(false);
        mGatewayPref.setVisible(false);
        mDnsPref.setVisible(false);

        Network currentNetwork = mWifiManager.getCurrentNetwork();
        if (currentNetwork == null) {
            return;
        }

        LinkProperties linkProperties = mConnectivityManager.getLinkProperties(currentNetwork);
        if (linkProperties == null) {
            return;
        }
        List<InetAddress> addresses = linkProperties.getAddresses();

        // Set IPv4 and Ipv6 addresses
        for (int i = 0; i < addresses.size(); i++) {
            InetAddress addr = addresses.get(i);
            if (addr instanceof Inet4Address) {
                mIpAddressPref.setDetailText(addr.getHostAddress());
                mIpAddressPref.setVisible(true);
            } else if (addr instanceof Inet6Address) {
                String ip = addr.getHostAddress();
                Preference pref = new Preference(mPrefContext);
                pref.setKey(ip);
                pref.setTitle(ip);
                mIpv6AddressCategory.addPreference(pref);
                mIpv6AddressCategory.setVisible(true);
            }
        }

        // Set up IPv4 gateway and subnet mask
        String gateway = null;
        String subnet = null;
        for (RouteInfo routeInfo : linkProperties.getRoutes()) {
            if (routeInfo.hasGateway() && routeInfo.getGateway() instanceof Inet4Address) {
                gateway = routeInfo.getGateway().getHostAddress();
            }
            IpPrefix ipPrefix = routeInfo.getDestination();
            if (ipPrefix != null && ipPrefix.getAddress() instanceof Inet4Address
                    && ipPrefix.getPrefixLength() > 0) {
                subnet = ipv4PrefixLengthToSubnetMask(ipPrefix.getPrefixLength());
            }
        }

        if (!TextUtils.isEmpty(subnet)) {
            mSubnetPref.setDetailText(subnet);
            mSubnetPref.setVisible(true);
        }

        if (!TextUtils.isEmpty(gateway)) {
            mGatewayPref.setDetailText(gateway);
            mGatewayPref.setVisible(true);
        }

        // Set IPv4 DNS addresses
        StringJoiner stringJoiner = new StringJoiner(",");
        for (InetAddress dnsServer : linkProperties.getDnsServers()) {
            if (dnsServer instanceof Inet4Address) {
                stringJoiner.add(dnsServer.getHostAddress());
            }
        }
        String dnsText = stringJoiner.toString();
        if (!dnsText.isEmpty()) {
            mDnsPref.setDetailText(dnsText);
            mDnsPref.setVisible(true);
        }
    }

    private static String ipv4PrefixLengthToSubnetMask(int prefixLength) {
        try {
            InetAddress all = InetAddress.getByAddress(
                    new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255});
            return NetworkUtils.getNetworkPart(all, prefixLength).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Returns whether the network represented by this preference can be forgotten.
     */
    public boolean canForgetNetwork() {
        return mWifiInfo != null && mWifiInfo.isEphemeral() || mWifiConfig != null;
    }

    /**
     * Forgets the wifi network associated with this preference.
     */
    public void forgetNetwork() {
        if (mWifiInfo != null && mWifiInfo.isEphemeral()) {
            mWifiManager.disableEphemeralNetwork(mWifiInfo.getSSID());
        } else if (mWifiConfig != null) {
            if (mWifiConfig.isPasspoint()) {
                mWifiManager.removePasspointConfiguration(mWifiConfig.FQDN);
            } else {
                mWifiManager.forget(mWifiConfig.networkId, null /* action listener */);
            }
        }
    }
}
