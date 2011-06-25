/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.email.activity;

import com.android.email.Email;
import com.android.email.MessageListContext;
import com.android.email.R;
import com.android.email.RefreshManager;
import com.android.email.activity.setup.AccountSettings;
import com.android.emailcommon.Logging;
import com.android.emailcommon.provider.Account;
import com.android.emailcommon.provider.EmailContent.Message;
import com.android.emailcommon.provider.HostAuth;
import com.android.emailcommon.provider.Mailbox;
import com.android.emailcommon.utility.EmailAsyncTask;
import com.android.emailcommon.utility.Utility;
import com.google.common.base.Objects;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.util.LinkedList;
import java.util.List;

/**
 * Base class for the UI controller.
 */
abstract class UIControllerBase implements MailboxListFragment.Callback,
        MessageListFragment.Callback, MessageViewFragment.Callback  {
    static final boolean DEBUG_FRAGMENTS = false; // DO NOT SUBMIT WITH TRUE

    static final String KEY_LIST_CONTEXT = "UIControllerBase.listContext";

    /** The owner activity */
    final EmailActivity mActivity;
    final FragmentManager mFragmentManager;

    protected final ActionBarController mActionBarController;

    final EmailAsyncTask.Tracker mTaskTracker = new EmailAsyncTask.Tracker();

    final RefreshManager mRefreshManager;

    /**
     * Fragments that are installed.
     *
     * A fragment is installed in {@link Fragment#onActivityCreated} and uninstalled in
     * {@link Fragment#onDestroyView}, using {@link FragmentInstallable} callbacks.
     *
     * This means fragments in the back stack are *not* installed.
     *
     * We set callbacks to fragments only when they are installed.
     *
     * @see FragmentInstallable
     */
    private MailboxListFragment mMailboxListFragment;
    private MessageListFragment mMessageListFragment;
    private MessageViewFragment mMessageViewFragment;

    /**
     * To avoid double-deleting a fragment (which will cause a runtime exception),
     * we put a fragment in this list when we {@link FragmentTransaction#remove(Fragment)} it,
     * and remove from the list when we actually uninstall it.
     */
    private final List<Fragment> mRemovedFragments = new LinkedList<Fragment>();

    /**
     * The active context for the current MessageList.
     * In some UI layouts such as the one-pane view, the message list may not be visible, but is
     * on the backstack. This list context will still be accessible in those cases.
     *
     * Should be set using {@link #setListContext(MessageListContext)}.
     */
    protected MessageListContext mListContext;

    private final RefreshManager.Listener mRefreshListener
            = new RefreshManager.Listener() {
        @Override
        public void onMessagingError(final long accountId, long mailboxId, final String message) {
            refreshActionBar();
        }

        @Override
        public void onRefreshStatusChanged(long accountId, long mailboxId) {
            refreshActionBar();
        }
    };

    public UIControllerBase(EmailActivity activity) {
        mActivity = activity;
        mFragmentManager = activity.getFragmentManager();
        mRefreshManager = RefreshManager.getInstance(mActivity);
        mActionBarController = createActionBarController(activity);
        if (DEBUG_FRAGMENTS) {
            FragmentManager.enableDebugLogging(true);
        }
    }

    /**
     * Called by the base class to let a subclass create an {@link ActionBarController}.
     */
    protected abstract ActionBarController createActionBarController(Activity activity);

    /** @return the layout ID for the activity. */
    public abstract int getLayoutId();

    /**
     * Must be called just after the activity sets up the content view.  Used to initialize views.
     *
     * (Due to the complexity regarding class/activity initialization order, we can't do this in
     * the constructor.)
     */
    public void onActivityViewReady() {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onActivityViewReady");
        }
    }

    /**
     * Called at the end of {@link EmailActivity#onCreate}.
     */
    public void onActivityCreated() {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onActivityCreated");
        }
        mRefreshManager.registerListener(mRefreshListener);
        mActionBarController.onActivityCreated();
    }

    /**
     * Handles the {@link android.app.Activity#onStart} callback.
     */
    public void onActivityStart() {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onActivityStart");
        }
    }

    /**
     * Handles the {@link android.app.Activity#onResume} callback.
     */
    public void onActivityResume() {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onActivityResume");
        }
        refreshActionBar();
    }

    /**
     * Handles the {@link android.app.Activity#onPause} callback.
     */
    public void onActivityPause() {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onActivityPause");
        }
    }

    /**
     * Handles the {@link android.app.Activity#onStop} callback.
     */
    public void onActivityStop() {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onActivityStop");
        }
    }

    /**
     * Handles the {@link android.app.Activity#onDestroy} callback.
     */
    public void onActivityDestroy() {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onActivityDestroy");
        }
        mActionBarController.onActivityDestroy();
        mRefreshManager.unregisterListener(mRefreshListener);
        mTaskTracker.cancellAllInterrupt();
    }

    /**
     * Handles the {@link android.app.Activity#onSaveInstanceState} callback.
     */
    public void onSaveInstanceState(Bundle outState) {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onSaveInstanceState");
        }
        mActionBarController.onSaveInstanceState(outState);
        outState.putParcelable(KEY_LIST_CONTEXT, mListContext);
    }

    /**
     * Handles the {@link android.app.Activity#onRestoreInstanceState} callback.
     */
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " restoreInstanceState");
        }
        mActionBarController.onRestoreInstanceState(savedInstanceState);
        mListContext = savedInstanceState.getParcelable(KEY_LIST_CONTEXT);
    }

    /**
     * Install a fragment.  Must be caleld from the host activity's
     * {@link FragmentInstallable#onInstallFragment}.
     */
    public final void onInstallFragment(Fragment fragment) {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onInstallFragment  fragment=" + fragment);
        }
        if (fragment instanceof MailboxListFragment) {
            installMailboxListFragment((MailboxListFragment) fragment);
        } else if (fragment instanceof MessageListFragment) {
            installMessageListFragment((MessageListFragment) fragment);
        } else if (fragment instanceof MessageViewFragment) {
            installMessageViewFragment((MessageViewFragment) fragment);
        } else {
            throw new IllegalArgumentException("Tried to install unknown fragment");
        }
    }

    /** Install fragment */
    protected void installMailboxListFragment(MailboxListFragment fragment) {
        mMailboxListFragment = fragment;
        mMailboxListFragment.setCallback(this);
        refreshActionBar();
    }

    /** Install fragment */
    protected void installMessageListFragment(MessageListFragment fragment) {
        mMessageListFragment = fragment;
        mMessageListFragment.setCallback(this);
        refreshActionBar();
    }

    /** Install fragment */
    protected void installMessageViewFragment(MessageViewFragment fragment) {
        mMessageViewFragment = fragment;
        mMessageViewFragment.setCallback(this);
        refreshActionBar();
    }

    /**
     * Uninstall a fragment.  Must be caleld from the host activity's
     * {@link FragmentInstallable#onUninstallFragment}.
     */
    public final void onUninstallFragment(Fragment fragment) {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " onUninstallFragment  fragment=" + fragment);
        }
        mRemovedFragments.remove(fragment);
        if (fragment == mMailboxListFragment) {
            uninstallMailboxListFragment();
        } else if (fragment == mMessageListFragment) {
            uninstallMessageListFragment();
        } else if (fragment == mMessageViewFragment) {
            uninstallMessageViewFragment();
        } else {
            throw new IllegalArgumentException("Tried to uninstall unknown fragment");
        }
    }

    /** Uninstall {@link MailboxListFragment} */
    protected void uninstallMailboxListFragment() {
        mMailboxListFragment.setCallback(null);
        mMailboxListFragment = null;
    }

    /** Uninstall {@link MessageListFragment} */
    protected void uninstallMessageListFragment() {
        mMessageListFragment.setCallback(null);
        mMessageListFragment = null;
    }

    /** Uninstall {@link MessageViewFragment} */
    protected void uninstallMessageViewFragment() {
        mMessageViewFragment.setCallback(null);
        mMessageViewFragment = null;
    }

    /**
     * If a {@link Fragment} is not already in {@link #mRemovedFragments},
     * {@link FragmentTransaction#remove} it and add to the list.
     *
     * Do nothing if {@code fragment} is null.
     */
    protected final void removeFragment(FragmentTransaction ft, Fragment fragment) {
        if (Logging.DEBUG_LIFECYCLE && Email.DEBUG) {
            Log.d(Logging.LOG_TAG, this + " removeFragment fragment=" + fragment);
        }
        if (fragment == null) {
            return;
        }
        if (!mRemovedFragments.contains(fragment)) {
            // STOPSHIP Remove log/catch.  b/4905749.
            Log.d(Logging.LOG_TAG, "Removing " + fragment);
            try {
                ft.remove(fragment);
            } catch (RuntimeException ex) {
                Log.e(Logging.LOG_TAG, "Got RuntimeException trying to remove fragment: "
                        + fragment, ex);
                Log.e(Logging.LOG_TAG, Utility.dumpFragment(fragment));
                throw ex;
            }
            addFragmentToRemovalList(fragment);
        }
    }

    /**
     * Remove a {@link Fragment} from {@link #mRemovedFragments}.  No-op if {@code fragment} is
     * null.
     *
     * {@link #removeMailboxListFragment}, {@link #removeMessageListFragment} and
     * {@link #removeMessageViewFragment} all call this, so subclasses don't have to do this when
     * using them.
     *
     * However, unfortunately, subclasses have to call this manually when popping from the
     * back stack to avoid double-delete.
     */
    protected void addFragmentToRemovalList(Fragment fragment) {
        if (fragment != null) {
            mRemovedFragments.add(fragment);
        }
    }

    /**
     * Remove the fragment if it's installed.
     */
    protected FragmentTransaction removeMailboxListFragment(FragmentTransaction ft) {
        removeFragment(ft, mMailboxListFragment);
        return ft;
    }

    /**
     * Remove the fragment if it's installed.
     */
    protected FragmentTransaction removeMessageListFragment(FragmentTransaction ft) {
        removeFragment(ft, mMessageListFragment);
        return ft;
    }

    /**
     * Remove the fragment if it's installed.
     */
    protected FragmentTransaction removeMessageViewFragment(FragmentTransaction ft) {
        removeFragment(ft, mMessageViewFragment);
        return ft;
    }

    /** @return true if a {@link MailboxListFragment} is installed. */
    protected final boolean isMailboxListInstalled() {
        return mMailboxListFragment != null;
    }

    /** @return true if a {@link MessageListFragment} is installed. */
    protected final boolean isMessageListInstalled() {
        return mMessageListFragment != null;
    }

    /** @return true if a {@link MessageViewFragment} is installed. */
    protected final boolean isMessageViewInstalled() {
        return mMessageViewFragment != null;
    }

    /** @return the installed {@link MailboxListFragment} or null. */
    protected final MailboxListFragment getMailboxListFragment() {
        return mMailboxListFragment;
    }

    /** @return the installed {@link MessageListFragment} or null. */
    protected final MessageListFragment getMessageListFragment() {
        return mMessageListFragment;
    }

    /** @return the installed {@link MessageViewFragment} or null. */
    protected final MessageViewFragment getMessageViewFragment() {
        return mMessageViewFragment;
    }

    /**
     * @return the currently selected account ID, *or* {@link Account#ACCOUNT_ID_COMBINED_VIEW}.
     *
     * @see #getActualAccountId()
     */
    public abstract long getUIAccountId();

    /**
     * @return true if an account is selected, or the current view is the combined view.
     */
    public final boolean isAccountSelected() {
        return getUIAccountId() != Account.NO_ACCOUNT;
    }

    /**
     * @return if an actual account is selected.  (i.e. {@link Account#ACCOUNT_ID_COMBINED_VIEW}
     * is not considered "actual".s)
     */
    public final boolean isActualAccountSelected() {
        return isAccountSelected() && (getUIAccountId() != Account.ACCOUNT_ID_COMBINED_VIEW);
    }

    /**
     * @return the currently selected account ID.  If the current view is the combined view,
     * it'll return {@link Account#NO_ACCOUNT}.
     *
     * @see #getUIAccountId()
     */
    public final long getActualAccountId() {
        return isActualAccountSelected() ? getUIAccountId() : Account.NO_ACCOUNT;
    }

    /**
     * Show the default view for the given account.
     *
     * No-op if the given account is already selected.
     *
     * @param accountId ID of the account to load.  Can be {@link Account#ACCOUNT_ID_COMBINED_VIEW}.
     *     Must never be {@link Account#NO_ACCOUNT}.
     */
    public final void switchAccount(long accountId) {

        if (Account.isSecurityHold(mActivity, accountId)) {
            ActivityHelper.showSecurityHoldDialog(mActivity, accountId);
            mActivity.finish();
            return;
        }

        if (accountId == getUIAccountId()) {
            // Do nothing if the account is already selected.  Not even going back to the inbox.
            return;
        }

        if (accountId == Account.ACCOUNT_ID_COMBINED_VIEW) {
            openMailbox(accountId, Mailbox.QUERY_ALL_INBOXES);
        } else {
            long inboxId = Mailbox.findMailboxOfType(mActivity, accountId, Mailbox.TYPE_INBOX);
            if (inboxId == Mailbox.NO_MAILBOX) {
                // The account doesn't have Inbox yet... Redirect to Welcome and let it wait for
                // the initial sync...
                Log.w(Logging.LOG_TAG, "Account " + accountId +" doesn't have Inbox.  Redirecting"
                        + " to Welcome...");
                Welcome.actionOpenAccountInbox(mActivity, accountId);
                mActivity.finish();
                return;
            } else {
                openMailbox(accountId, inboxId);
            }
        }
    }

    /**
     * Returns the id of the parent mailbox used for the mailbox list fragment.
     *
     * IMPORTANT: Do not confuse {@link #getMailboxListMailboxId()} with
     *     {@link #getMessageListMailboxId()}
     */
    protected long getMailboxListMailboxId() {
        return isMailboxListInstalled() ? getMailboxListFragment().getSelectedMailboxId()
                : Mailbox.NO_MAILBOX;
    }

    /**
     * Returns the id of the mailbox used for the message list fragment.
     *
     * IMPORTANT: Do not confuse {@link #getMailboxListMailboxId()} with
     *     {@link #getMessageListMailboxId()}
     */
    protected long getMessageListMailboxId() {
        return isMessageListInstalled() ? getMessageListFragment().getMailboxId()
                : Mailbox.NO_MAILBOX;
    }

    /**
     * Shortcut for {@link #open} with {@link Message#NO_MESSAGE}.
     */
    protected final void openMailbox(long accountId, long mailboxId) {
        open(MessageListContext.forMailbox(accountId, mailboxId), Message.NO_MESSAGE);
    }

    /**
     * Opens a given list
     * @param listContext the list context for the message list to open
     * @param messageId if specified and not {@link Message#NO_MESSAGE}, will open the message
     *     in the message list.
     */
    public final void open(final MessageListContext listContext, final long messageId) {
        setListContext(listContext);
        openInternal(listContext, messageId);

        if (listContext.isSearch()) {
            mActionBarController.enterSearchMode(listContext.getSearchParams().mFilter);
        }
    }

    /**
     * Sets the internal value of the list context for the message list.
     */
    protected void setListContext(MessageListContext listContext) {
        if (Objects.equal(listContext, mListContext)) {
            return;
        }

        // TODO: remove this when the search mailbox no longer shows up on the list
        // Special case search. Since the search mailbox shows up in the mailbox list, the mailbox
        // list can give us a callback to open that mailbox, and it will look like a normal
        // mailbox open instead of a search, blowing away a perfectly good list context.
        if (mListContext != null
                && mListContext.isSearch()
                && mListContext.getMailboxId() == listContext.getMailboxId()) {
            return;
        }

        mListContext = listContext;
    }

    protected abstract void openInternal(
            final MessageListContext listContext, final long messageId);

    /**
     * Performs the back action.
     *
     * NOTE The method in the base class has precedence.  Subclasses overriding this method MUST
     * call super's method first.
     *
     * @param isSystemBackKey <code>true</code> if the system back key was pressed.
     * <code>false</code> if it's caused by the "home" icon click on the action bar.
     */
    public boolean onBackPressed(boolean isSystemBackKey) {
        if (mActionBarController.onBackPressed(isSystemBackKey)) {
            return true;
        }
        return false;
    }

    /**
     * Must be called from {@link Activity#onSearchRequested()}.
     * This initiates the search entry mode - see {@link #onSearchSubmit} for when the search
     * is actually submitted.
     */
    public void onSearchRequested() {
        mActionBarController.enterSearchMode(null);
    }

    /** @return true if the search menu option should be enabled based on the current UI. */
    protected boolean canSearch() {
        return false;
    }

    /**
     * Kicks off a search query, if the UI is in a state where a search is possible.
     */
    protected void onSearchSubmit(final String queryTerm) {
        final long accountId = getUIAccountId();
        if (!Account.isNormalAccount(accountId)) {
            return; // Invalid account to search from.
        }

        // TODO: do a global search for EAS inbox.
        // TODO: handle doing another search from a search result, in which case we should
        //       search the original mailbox that was searched, and not search in the search mailbox
        final long mailboxId = getMessageListMailboxId();

        if (Email.DEBUG) {
            Log.d(Logging.LOG_TAG, "Submitting search: " + queryTerm);
        }

        mActivity.startActivity(EmailActivity.createSearchIntent(
                mActivity, accountId, mailboxId, queryTerm));


        // TODO: this causes a slight flicker.
        // A new instance of the activity will sit on top. When the user exits search and
        // returns to this activity, the search box should not be open then.
        mActionBarController.exitSearchMode();
    }

    /**
     * Handles exiting of search entry mode.
     */
    protected void onSearchExit() {
        if ((mListContext != null) && mListContext.isSearch()) {
            mActivity.finish();
        }
    }

    /**
     * Handles the {@link android.app.Activity#onCreateOptionsMenu} callback.
     */
    public boolean onCreateOptionsMenu(MenuInflater inflater, Menu menu) {
        inflater.inflate(R.menu.email_activity_options, menu);
        return true;
    }

    /**
     * Handles the {@link android.app.Activity#onPrepareOptionsMenu} callback.
     */
    public boolean onPrepareOptionsMenu(MenuInflater inflater, Menu menu) {

        // Update the refresh button.
        MenuItem item = menu.findItem(R.id.refresh);
        if (isRefreshEnabled()) {
            item.setVisible(true);
            if (isRefreshInProgress()) {
                item.setActionView(R.layout.action_bar_indeterminate_progress);
            } else {
                item.setActionView(null);
            }
        } else {
            item.setVisible(false);
        }

        // Deal with protocol-specific menu options.
        boolean isEas = false;
        boolean accountSearchable = false;
        long accountId = getActualAccountId();
        if (accountId > 0) {
            Account account = Account.restoreAccountWithId(mActivity, accountId);
            if (account != null) {
                String protocol = account.getProtocol(mActivity);
                if (HostAuth.SCHEME_EAS.equals(protocol)) {
                    isEas = true;
                }
                accountSearchable = (account.mFlags & Account.FLAGS_SUPPORTS_SEARCH) != 0;
            }
        }

        // TODO: Should use an isSyncable call to prevent drafts/outbox from allowing this
        menu.findItem(R.id.search).setVisible(accountSearchable && canSearch());
        menu.findItem(R.id.sync_lookback).setVisible(isEas);
        menu.findItem(R.id.sync_frequency).setVisible(isEas);

        return true;
    }

    /**
     * Handles the {@link android.app.Activity#onOptionsItemSelected} callback.
     *
     * @return true if the option item is handled.
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Comes from the action bar when the app icon on the left is pressed.
                // It works like a back press, but it won't close the activity.
                return onBackPressed(false);
            case R.id.compose:
                return onCompose();
            case R.id.refresh:
                onRefresh();
                return true;
            case R.id.account_settings:
                return onAccountSettings();
            case R.id.search:
                onSearchRequested();
                return true;
        }
        return false;
    }

    /**
     * Opens the message compose activity.
     */
    private boolean onCompose() {
        if (!isAccountSelected()) {
            return false; // this shouldn't really happen
        }
        MessageCompose.actionCompose(mActivity, getActualAccountId());
        return true;
    }

    /**
     * Handles the "Settings" option item.  Opens the settings activity.
     */
    private boolean onAccountSettings() {
        AccountSettings.actionSettings(mActivity, getActualAccountId());
        return true;
    }

    /**
     * @return the ID of the message in focus and visible, if any. Returns
     *     {@link Message#NO_MESSAGE} if no message is opened.
     */
    protected long getMessageId() {
        return isMessageViewInstalled()
                ? getMessageViewFragment().getMessageId()
                : Message.NO_MESSAGE;
    }


    /**
     * STOPSHIP For experimental UI.  Remove this.
     *
     * @return mailbox ID for "mailbox settings" option.
     */
    public abstract long getMailboxSettingsMailboxId();

    /**
     * STOPSHIP For experimental UI.  Make it abstract protected.
     *
     * Performs "refesh".
     */
    public abstract void onRefresh();

    /**
     * @return true if refresh is in progress for the current mailbox.
     */
    protected abstract boolean isRefreshInProgress();

    /**
     * @return true if the UI should enable the "refresh" command.
     */
    protected abstract boolean isRefreshEnabled();

    /**
     * Refresh the action bar and menu items, including the "refreshing" icon.
     */
    protected void refreshActionBar() {
        if (mActionBarController != null) {
            mActionBarController.refresh();
        }
        mActivity.invalidateOptionsMenu();
    }


    @Override
    public String toString() {
        return getClass().getSimpleName(); // Shown on logcat
    }
}
