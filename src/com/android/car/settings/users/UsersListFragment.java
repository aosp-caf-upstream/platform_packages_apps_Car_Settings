/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.car.settings.users;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.car.widget.ListItemProvider;

import com.android.car.settings.R;
import com.android.car.settings.accounts.UserDetailsFragment;
import com.android.car.settings.common.ListItemSettingsFragment;
import com.android.settingslib.users.UserManagerHelper;

/**
 * Lists all Users available on this device.
 */
public class UsersListFragment extends ListItemSettingsFragment
        implements UserManagerHelper.OnUsersUpdateListener,
        UsersItemProvider.UserClickListener,
        ConfirmCreateNewUserDialog.ConfirmCreateNewUserListener,
        ConfirmExitRetailModeDialog.ConfirmExitRetailModeListener {
    private static final String FACTORY_RESET_PACKAGE_NAME = "android";
    private static final String FACTORY_RESET_REASON = "ExitRetailModeConfirmed";
    private static final String TAG = "UsersListFragment";

    private UsersItemProvider mItemProvider;
    private UserManagerHelper mUserManagerHelper;

    private ProgressBar mProgressBar;
    private Button mAddUserButton;

    private AsyncTask mAddNewUserTask;

    public static UsersListFragment newInstance() {
        UsersListFragment usersListFragment = new UsersListFragment();
        Bundle bundle = ListItemSettingsFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.user_and_account_settings_title);
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar_with_button);
        usersListFragment.setArguments(bundle);
        return usersListFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mUserManagerHelper = new UserManagerHelper(getContext());
        mItemProvider =
                new UsersItemProvider(getContext(), this, mUserManagerHelper);

        // Register to receive changes to the users.
        mUserManagerHelper.registerOnUsersUpdateListener(this);

        // Super class's onActivityCreated need to be called after itemProvider is initialized.
        // Because getItemProvider is called in there.
        super.onActivityCreated(savedInstanceState);

        mProgressBar = getActivity().findViewById(R.id.progress_bar);

        mAddUserButton = (Button) getActivity().findViewById(R.id.action_button1);
        if (mUserManagerHelper.currentProcessRunningAsDemoUser()) {
            // If the user is a demo user, show a dialog asking if they want to exit retail/demo
            // mode
            mAddUserButton.setText(R.string.exit_retail_button_text);
            mAddUserButton.setOnClickListener(v -> {
                ConfirmExitRetailModeDialog dialog = new ConfirmExitRetailModeDialog();
                dialog.setConfirmExitRetailModeListener(this);
                dialog.show(this);
            });
        } else if (mUserManagerHelper.currentProcessCanAddUsers()) {
            // Only add the add user button if the current user is allowed to add a user.
            mAddUserButton.setText(R.string.user_add_user_menu);
            mAddUserButton.setOnClickListener(v -> {
                ConfirmCreateNewUserDialog dialog =
                        new ConfirmCreateNewUserDialog();
                dialog.setConfirmCreateNewUserListener(this);
                dialog.show(this);
            });
        }
    }

    @Override
    public void onCreateNewUserConfirmed() {
        mAddNewUserTask =
                new AddNewUserTask().execute(getContext().getString(R.string.user_new_user_name));
    }

    /**
     * Will perform a factory reset. Copied from
     * {@link com.android.settings.MasterClearConfirm#doMasterClear()}
     */
    @Override
    public void onExitRetailModeConfirmed() {
        Intent intent = new Intent(Intent.ACTION_FACTORY_RESET);
        intent.setPackage(FACTORY_RESET_PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(Intent.EXTRA_REASON, FACTORY_RESET_REASON);
        intent.putExtra(Intent.EXTRA_WIPE_EXTERNAL_STORAGE, true);
        intent.putExtra(Intent.EXTRA_WIPE_ESIMS, true);
        getActivity().sendBroadcast(intent);
        // Intent handling is asynchronous -- assume it will happen soon.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mAddNewUserTask != null) {
            mAddNewUserTask.cancel(false /* mayInterruptIfRunning */);
        }

        mUserManagerHelper.unregisterOnUsersUpdateListener();
    }

    @Override
    public void onUsersUpdate() {
        refreshListItems();
    }

    @Override
    public void onUserClicked(UserInfo userInfo) {
        if (mUserManagerHelper.userIsRunningCurrentProcess(userInfo)) {
            // If it's the user running the process, launch fragment that displays their accounts.
            getFragmentController().launchFragment(UserDetailsFragment.newInstance());
        } else {
            // If it's another user, launch fragment that displays their information
            getFragmentController().launchFragment(EditUsernameFragment.getInstance(userInfo));
        }
    }

    /**
     * User list fragment is distraction optimized, so is allowed at all times.
     */
    @Override
    public boolean canBeShown(CarUxRestrictions carUxRestrictions) {
        return true;
    }

    @Override
    public void onGuestClicked() {
        getFragmentController().launchFragment(GuestFragment.newInstance());
    }

    @Override
    public ListItemProvider getItemProvider() {
        return mItemProvider;
    }

    private void refreshListItems() {
        mItemProvider.refreshItems();
        refreshList();
    }

    private class AddNewUserTask extends AsyncTask<String, Void, UserInfo> {
        @Override
        protected UserInfo doInBackground(String... userNames) {
            return mUserManagerHelper.createNewUser(userNames[0]);
        }

        @Override
        protected void onPreExecute() {
            mAddUserButton.setEnabled(false);
            mProgressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(UserInfo user) {
            mAddUserButton.setEnabled(true);
            mProgressBar.setVisibility(View.GONE);
            if (user != null) {
                mUserManagerHelper.switchToUser(user);
            }
        }
    }
}
