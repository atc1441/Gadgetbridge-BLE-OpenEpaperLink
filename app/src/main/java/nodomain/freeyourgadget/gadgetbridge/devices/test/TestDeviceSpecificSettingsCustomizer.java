/*  Copyright (C) 2024 José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.test;

import android.content.Intent;
import android.os.Parcel;
import android.widget.Toast;

import androidx.preference.Preference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.test.activity.TestActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class TestDeviceSpecificSettingsCustomizer implements DeviceSpecificSettingsCustomizer {
    @Override
    public void onPreferenceChange(final Preference preference, final DeviceSpecificSettingsHandler handler) {
    }

    @Override
    public void customizeSettings(final DeviceSpecificSettingsHandler handler, final Prefs prefs, final String rootKey) {
        final Preference features = handler.findPreference(TestDeviceConst.PREF_TEST_FEATURES);
        if (features != null) {
            features.setOnPreferenceClickListener(preference -> {
                final Intent intent = new Intent(handler.getContext(), TestFeaturesActivity.class);
                intent.putExtra(GBDevice.EXTRA_DEVICE, handler.getDevice());
                handler.getContext().startActivity(intent);
                return true;
            });
        }

        final Preference addTestActivities = handler.findPreference("pref_developer_add_test_activities");
        if (addTestActivities != null) {
            addTestActivities.setOnPreferenceClickListener(preference -> {
                try (DBHandler dbHandler = GBApplication.acquireDB()) {
                    final DaoSession session = dbHandler.getDaoSession();
                    final Device device = DBHelper.getDevice(handler.getDevice(), session);
                    final User user = DBHelper.getUser(session);

                    //final QueryBuilder<?> qb = session.getBaseActivitySummaryDao().queryBuilder();
                    //qb.where(BaseActivitySummaryDao.Properties.DeviceId.eq(device.getId())).buildDelete().executeDeleteWithoutDetachingEntities();

                    final List<BaseActivitySummary> summaries = new ArrayList<>();

                    final TestActivitySummaryParser parser = new TestActivitySummaryParser();
                    long startTime = System.currentTimeMillis();

                    for (final ActivityKind activityKind : ActivityKind.values()) {
                        if (!isRecordedActivity(activityKind)) {
                            continue;
                        }

                        startTime -= TestDeviceRand.randLong(startTime, 4 * 60 * 60 * 1000L, 12 * 60 * 60 * 1000L);
                        final long duration = TestDeviceRand.randLong(startTime, 15 * 60 * 1000L, 2 * 60 * 60 * 1000L);

                        final BaseActivitySummary summary = new BaseActivitySummary();
                        summary.setStartTime(new Date(startTime));
                        summary.setEndTime(new Date(startTime + duration));
                        summary.setDevice(device);
                        summary.setUser(user);
                        summary.setActivityKind(activityKind.getCode());
                        summary.setName(activityKind.name());
                        parser.parseBinaryData(summary, false);
                        summaries.add(summary);
                    }

                    session.getBaseActivitySummaryDao().insertOrReplaceInTx(summaries);
                } catch (final Exception e) {
                    GB.toast(handler.getContext(), "Error saving activity summary", Toast.LENGTH_LONG, GB.ERROR, e);
                    return false;
                }

                return true;
            });
        }
    }

    /**
     * Whether a device records an activity of this kind. Sleep and the states of the wearer are
     * not recorded activities.
     */
    private static boolean isRecordedActivity(final ActivityKind activityKind) {
        return switch (activityKind) {
            case NOT_MEASURED, UNKNOWN, ACTIVITY, NOT_WORN, TRANSITION, VIVOMOVE_HR_TRANSITION -> false;
            default -> !ActivityKind.isSleep(activityKind);
        };
    }

    @Override
    public Set<String> getPreferenceKeysWithSummary() {
        return Collections.emptySet();
    }

    public static final Creator<TestDeviceSpecificSettingsCustomizer> CREATOR = new Creator<TestDeviceSpecificSettingsCustomizer>() {
        @Override
        public TestDeviceSpecificSettingsCustomizer createFromParcel(final Parcel in) {
            return new TestDeviceSpecificSettingsCustomizer();
        }

        @Override
        public TestDeviceSpecificSettingsCustomizer[] newArray(final int size) {
            return new TestDeviceSpecificSettingsCustomizer[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
    }
}
