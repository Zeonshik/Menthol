package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MentholVpnCore;
import org.telegram.messenger.MentholVpnService;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MentholVpnActivity extends BaseFragment {
    private static final int MENU_MORE = 2;
    private static final int MENU_ADD_SUBSCRIPTION = 3;
    private static final int MENU_PASTE_CLIPBOARD = 4;
    private static final int MENU_PASTE_JSON = 7;
    private static final int MENU_COPY_JSON = 8;
    private static final int MENU_PING_TEST = 9;
    private RecyclerListView listView;
    private Adapter adapter;
    private TextView ourServersTab;
    private TextView subscriptionsTab;
    private TextView startButton;
    private TextView vpnConflictBanner;
    private ActionBarMenuItem moreItem;
    private int selectedTab = 0;
    private boolean loadingSubscription;
    private boolean vpnStarted;
    private boolean pingInProgress;
    private boolean conflictDialogShown;
    private boolean builtInExpanded = true;
    private String selectedConfigRaw;
    private final Runnable externalVpnMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            handleExternalVpnDrop();
            AndroidUtilities.runOnUIThread(this, 2500);
        }
    };
    private ArrayList<MentholVpnConfigParser.ParsedConfig> builtInConfigs = new ArrayList<>();
    private final HashSet<String> expandedSubscriptions = new HashSet<>();
    private ArrayList<MentholVpnSubscriptionStore.Subscription> subscriptions = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        subscriptions = MentholVpnSubscriptionStore.load();
        builtInConfigs = MentholVpnBuiltinServers.getConfigs();
        selectedConfigRaw = MentholVpnSubscriptionStore.getSelectedConfigRaw();
        vpnStarted = MentholVpnSubscriptionStore.isRunning();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        vpnStarted = MentholVpnSubscriptionStore.isRunning();
        selectedConfigRaw = MentholVpnSubscriptionStore.getSelectedConfigRaw();
        updateStartButton();
        updateVpnConflictBanner();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        AndroidUtilities.cancelRunOnUIThread(externalVpnMonitorRunnable);
        AndroidUtilities.runOnUIThread(externalVpnMonitorRunnable, 2500);
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.cancelRunOnUIThread(externalVpnMonitorRunnable);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(t("VPN Proxy", "VPN Proxy"));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_ADD_SUBSCRIPTION) {
                    showAddSubscriptionDialog();
                } else if (id == MENU_PASTE_CLIPBOARD) {
                    importFromClipboard(false);
                } else if (id == MENU_PASTE_JSON) {
                    showManualInputDialog(true);
                } else if (id == MENU_COPY_JSON) {
                    copyFirstConfigJson();
                } else if (id == MENU_PING_TEST) {
                    pingAllServers();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        moreItem = menu.addItem(MENU_MORE, R.drawable.ic_ab_other);
        moreItem.addSubItem(MENU_ADD_SUBSCRIPTION, R.drawable.msg_add, t("Добавить подписку", "Add subscription"));
        moreItem.addSubItem(MENU_PASTE_CLIPBOARD, R.drawable.msg_copy, t("Вставить из буфера обмена", "Paste from clipboard"));
        moreItem.addSubItem(MENU_PASTE_JSON, R.drawable.msg_copy, t("Вставить JSON", "Paste JSON"));
        moreItem.addSubItem(MENU_COPY_JSON, R.drawable.msg_copy, t("Копировать JSON", "Copy JSON"));
        moreItem.addSubItem(MENU_PING_TEST, R.drawable.msg_speed, t("Пинг тест", "Ping test"));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(pageBgColor());
        fragmentView = frameLayout;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        frameLayout.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        content.addView(tabs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58));

        ourServersTab = createTab(context, t("Наши сервера", "Our servers"), 0);
        subscriptionsTab = createTab(context, t("Ваши подписки", "Your subscriptions"), 1);
        tabs.addView(ourServersTab, LayoutHelper.createLinear(0, 40, 1f, 0, 0, 6, 0));
        tabs.addView(subscriptionsTab, LayoutHelper.createLinear(0, 40, 1f, 6, 0, 0, 0));

        vpnConflictBanner = new TextView(context);
        vpnConflictBanner.setTextSize(14);
        vpnConflictBanner.setTextColor(textColor());
        vpnConflictBanner.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        vpnConflictBanner.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14), cardBgColor(), Theme.getColor(Theme.key_listSelector), Color.BLACK));
        vpnConflictBanner.setOnClickListener(v -> {
            if (MentholVpnSubscriptionStore.isExternalVpnPreferred()) {
                showBulletin(t("Отключите сторонний VPN что бы использовать встроенный", "Turn off the third-party VPN to use the built-in one"));
            } else {
                showVpnConflictDialog();
            }
        });
        content.addView(vpnConflictBanner, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 8));

        startButton = new TextView(context);
        startButton.setGravity(Gravity.CENTER);
        startButton.setTextSize(16);
        startButton.setTypeface(AndroidUtilities.bold());
        startButton.setOnClickListener(v -> toggleStart());
        content.addView(startButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 4, 16, 8));
        updateStartButton();

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setAddDuration(180);
        itemAnimator.setRemoveDuration(160);
        itemAnimator.setMoveDuration(180);
        itemAnimator.setChangeDuration(120);
        listView.setItemAnimator(itemAnimator);
        listView.setAdapter(adapter = new Adapter(context));
        content.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        updateTabs();
        updateVpnConflictBanner();
        AndroidUtilities.cancelRunOnUIThread(externalVpnMonitorRunnable);
        AndroidUtilities.runOnUIThread(externalVpnMonitorRunnable, 2500);
        AndroidUtilities.runOnUIThread(() -> {
            if (!conflictDialogShown && isThirdPartyVpnActive() && !MentholVpnSubscriptionStore.isExternalVpnPreferred()) {
                conflictDialogShown = true;
                showVpnConflictDialog();
            }
        }, 350);

        return fragmentView;
    }

    private TextView createTab(Context context, String text, int tab) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(AndroidUtilities.bold());
        view.setOnClickListener(v -> {
            switchTab(tab);
        });
        return view;
    }

    private void switchTab(int tab) {
        if (selectedTab == tab) {
            return;
        }
        selectedTab = tab;
        updateTabs();
        if (listView == null || adapter == null) {
            return;
        }
        listView.animate().cancel();
        listView.animate().alpha(0f).setDuration(90).withEndAction(() -> {
            adapter.notifyDataSetChanged();
            listView.animate().alpha(1f).setDuration(130).start();
        }).start();
    }

    private void toggleStart() {
        if (vpnStarted) {
            stopVpn();
            return;
        }
        boolean thirdPartyVpnActive = isThirdPartyVpnActive();
        if (!thirdPartyVpnActive && MentholVpnSubscriptionStore.isExternalVpnPreferred()) {
            MentholVpnSubscriptionStore.setExternalVpnPreferred(false);
            updateVpnConflictBanner();
        }
        if (thirdPartyVpnActive && MentholVpnSubscriptionStore.isExternalVpnPreferred()) {
            showBulletin(t("Отключите сторонний VPN что бы использовать встроенный", "Turn off the third-party VPN to use the built-in one"));
            return;
        }
        if (thirdPartyVpnActive) {
            showVpnConflictDialog();
            return;
        }
        if (TextUtils.isEmpty(selectedConfigRaw)) {
            showBulletin(t("Выберите сервер", "Choose a server"));
            return;
        }
        startVpn();
    }

    private void startVpn() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        String config;
        try {
            config = MentholVpnConfigBuilder.build(selectedConfigRaw);
        } catch (Exception e) {
            showBulletin(t("Не удалось собрать Xray config", "Failed to build Xray config"));
            return;
        }
        startVpnService(config);
    }

    private void startVpnService(String config) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, MentholVpnService.class);
        intent.setAction(MentholVpnService.ACTION_START);
        intent.putExtra(MentholVpnService.EXTRA_CONFIG, config);
        ContextCompat.startForegroundService(context, intent);
        vpnStarted = true;
        MentholVpnSubscriptionStore.setRunning(true);
        updateStartButton();
        updateVpnConflictBanner();
        showBulletin(t("VPN запускается", "VPN is starting"));
    }

    private void stopVpn() {
        Context context = getContext();
        if (context != null) {
            Intent intent = new Intent(context, MentholVpnService.class);
            intent.setAction(MentholVpnService.ACTION_STOP);
            context.startService(intent);
        }
        vpnStarted = false;
        MentholVpnSubscriptionStore.setRunning(false);
        updateStartButton();
        updateVpnConflictBanner();
        showBulletin(t("VPN остановлен", "VPN stopped"));
    }

    private void handleExternalVpnDrop() {
        if (vpnStarted || !MentholVpnSubscriptionStore.isExternalVpnPreferred() || isThirdPartyVpnActive()) {
            return;
        }
        MentholVpnSubscriptionStore.setExternalVpnPreferred(false);
        if (TextUtils.isEmpty(selectedConfigRaw) && !builtInConfigs.isEmpty()) {
            selectedConfigRaw = builtInConfigs.get(0).raw;
            MentholVpnSubscriptionStore.setSelectedConfigRaw(selectedConfigRaw);
        }
        updateVpnConflictBanner();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (!TextUtils.isEmpty(selectedConfigRaw)) {
            showBulletin(t("Сторонний VPN отключился, запускаю встроенный", "External VPN disconnected, starting built-in VPN"));
            startVpn();
        } else {
            showBulletin(t("Сторонний VPN отключился, выберите сервер", "External VPN disconnected, choose a server"));
        }
    }

    private boolean isThirdPartyVpnActive() {
        if (vpnStarted || MentholVpnSubscriptionStore.isRunning()) {
            return false;
        }
        Context context = getContext();
        if (context == null || android.os.Build.VERSION.SDK_INT < 21) {
            return false;
        }
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return false;
            }
            Network[] networks = manager.getAllNetworks();
            for (Network network : networks) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    private void updateVpnConflictBanner() {
        if (vpnConflictBanner == null) {
            return;
        }
        boolean preferredExternal = MentholVpnSubscriptionStore.isExternalVpnPreferred();
        boolean thirdPartyVpnActive = isThirdPartyVpnActive();
        if (preferredExternal && !thirdPartyVpnActive) {
            MentholVpnSubscriptionStore.setExternalVpnPreferred(false);
            preferredExternal = false;
        }
        boolean show = preferredExternal || thirdPartyVpnActive;
        vpnConflictBanner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (preferredExternal) {
            vpnConflictBanner.setText(t("Отключите сторонний VPN что бы использовать встроенный", "Turn off the third-party VPN to use the built-in one"));
        } else {
            vpnConflictBanner.setText(t("Отключите сторонний VPN что бы использовать встроенный", "Turn off the third-party VPN to use the built-in one"));
        }
    }

    private void showVpnConflictDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(context)
                .setTitle(t("Конфликт VPN", "VPN conflict"))
                .setMessage(t("Отключите сторонний VPN что бы использовать встроенный", "Turn off the third-party VPN to use the built-in one"))
                .setNegativeButton(t("Использовать ваш", "Use yours"), (dialog, which) -> {
                    MentholVpnSubscriptionStore.setExternalVpnPreferred(true);
                    if (vpnStarted) {
                        stopVpn();
                    }
                    updateVpnConflictBanner();
                })
                .setPositiveButton(t("Использовать встроенный", "Use built-in"), (dialog, which) -> showDisableSystemVpnDialog())
                .create());
    }

    private void showDisableSystemVpnDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(context)
                .setTitle(t("Отключите сторонний VPN что бы использовать встроенный", "Turn off the third-party VPN to use the built-in one"))
                .setMessage(t("В настройках VPN выберите активную конфигурацию и отключите ее. После этого вернитесь в Menthol и запустите встроенный VPN.", "In VPN settings, select the active configuration and disconnect it. Then return to Menthol and start the built-in VPN."))
                .setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
                    MentholVpnSubscriptionStore.setExternalVpnPreferred(false);
                    openVpnSettings();
                    updateVpnConflictBanner();
                })
                .create());
    }

    private void openVpnSettings() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            context.startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
        } catch (Exception e) {
            try {
                context.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignore) {
            }
        }
    }


    private void updateStartButton() {
        if (startButton == null) {
            return;
        }
        int color = vpnStarted ? 0xffa94444 : selectedCardBgColor();
        startButton.setText(vpnStarted ? t("Остановить", "Stop") : t("Старт", "Start"));
        startButton.setTextColor(Color.WHITE);
        startButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(24), color, Theme.getColor(Theme.key_listSelector), Color.BLACK));
    }

    private void updateTabs() {
        int accent = selectedCardBgColor();
        int text = textColor();
        int inactive = cardBgColor();
        ourServersTab.setTextColor(selectedTab == 0 ? Color.WHITE : text);
        subscriptionsTab.setTextColor(selectedTab == 1 ? Color.WHITE : text);
        ourServersTab.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18), selectedTab == 0 ? accent : inactive, Theme.getColor(Theme.key_listSelector), Color.BLACK));
        subscriptionsTab.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18), selectedTab == 1 ? accent : inactive, Theme.getColor(Theme.key_listSelector), Color.BLACK));
    }

    private void showAddSubscriptionDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(6), AndroidUtilities.dp(24), 0);

        EditText nameField = new EditText(context);
        nameField.setHint(t("Название", "Name"));
        nameField.setSingleLine(true);
        layout.addView(nameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        EditText urlField = new EditText(context);
        urlField.setHint("https://example.com/sub");
        urlField.setSingleLine(true);
        urlField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(urlField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(t("Добавить подписку", "Add subscription"))
                .setView(layout)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Add), (d, which) -> {
                    String url = urlField.getText().toString().trim();
                    if (!TextUtils.isEmpty(url)) {
                        addSubscription(nameField.getText().toString().trim(), url);
                    }
                })
                .create();
        showDialog(dialog);
    }

    private void showManualInputDialog(boolean jsonOnly) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        EditText field = new EditText(context);
        field.setMinLines(4);
        field.setGravity(Gravity.TOP | Gravity.LEFT);
        field.setHint(jsonOnly ? "{ ... }" : t("vmess://, vless://, trojan://, ss:// или JSON", "vmess://, vless://, trojan://, ss:// or JSON"));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        container.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140));
        showDialog(new AlertDialog.Builder(context)
                .setTitle(jsonOnly ? t("Вставить JSON", "Paste JSON") : t("Ручной ввод", "Manual input"))
                .setView(container)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Add), (dialog, which) -> importRawText(field.getText().toString(), jsonOnly ? "JSON" : t("Ручной ввод", "Manual input")))
                .create());
    }

    private void importFromClipboard(boolean jsonOnly) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboardManager == null ? null : clipboardManager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            showBulletin(t("Буфер обмена пуст", "Clipboard is empty"));
            return;
        }
        CharSequence text = clipData.getItemAt(0).coerceToText(context);
        String value = text == null ? null : text.toString().trim();
        if (!jsonOnly && isSubscriptionUrl(value)) {
            addSubscription(null, value);
        } else {
            importRawText(value, jsonOnly ? t("JSON из буфера", "JSON from clipboard") : t("Буфер обмена", "Clipboard"));
        }
    }

    private boolean isSubscriptionUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private void importRawText(String text, String name) {
        ArrayList<MentholVpnConfigParser.ParsedConfig> configs = MentholVpnConfigParser.parseConfigText(text);
        if (configs.isEmpty()) {
            showBulletin(t("Не удалось распарсить конфиг", "Failed to parse config"));
            return;
        }
        if (containsAnyConfig(configs)) {
            showBulletin(t("Такой конфиг уже добавлен", "This config is already added"));
            return;
        }
        MentholVpnSubscriptionStore.addOrUpdate(name, "manual://" + System.currentTimeMillis(), configs);
        subscriptions = MentholVpnSubscriptionStore.load();
        selectedTab = 1;
        updateTabs();
        adapter.notifyDataSetChanged();
        showBulletin(t("Импортировано конфигов: ", "Imported configs: ") + configs.size());
    }

    private void copyFirstConfigJson() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        for (MentholVpnSubscriptionStore.Subscription subscription : subscriptions) {
            for (MentholVpnConfigParser.ParsedConfig config : subscription.configs) {
                if ("JSON".equals(config.type) && !TextUtils.isEmpty(config.raw)) {
                    ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("config", config.raw));
                    showBulletin("JSON скопирован");
                    return;
                }
            }
        }
        showBulletin("JSON-конфигов нет");
    }

    private void addSubscription(String name, String url) {
        if (loadingSubscription) {
            return;
        }
        loadingSubscription = true;
        adapter.notifyDataSetChanged();
        new Thread(() -> {
            String body = null;
            Exception error = null;
            try {
                body = downloadText(url);
            } catch (Exception e) {
                error = e;
            }
            String finalBody = body;
            Exception finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                loadingSubscription = false;
                if (finalError != null || TextUtils.isEmpty(finalBody)) {
                    showBulletin(t("Не удалось загрузить подписку", "Failed to load subscription"));
                } else {
                    ArrayList<MentholVpnConfigParser.ParsedConfig> configs = MentholVpnConfigParser.parseSubscription(finalBody);
                    if (containsSubscriptionUrl(url)) {
                        showBulletin(t("Подписка обновлена: ", "Subscription updated: ") + configs.size() + t(" конфигов", " configs"));
                    }
                    MentholVpnSubscriptionStore.addOrUpdate(name, url, configs);
                    subscriptions = MentholVpnSubscriptionStore.load();
                    for (MentholVpnSubscriptionStore.Subscription subscription : subscriptions) {
                        if (url.equals(subscription.url)) {
                            expandedSubscriptions.add(subscription.id);
                            break;
                        }
                    }
                    selectedTab = 1;
                    updateTabs();
                    showBulletin(t("Импортировано конфигов: ", "Imported configs: ") + configs.size());
                }
                adapter.notifyDataSetChanged();
            });
        }, "menthol-subscription-loader").start();
    }

    private String downloadText(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "v2rayN/6");
        connection.setRequestProperty("Accept-Encoding", "identity");
        try (BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream()); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }

    private boolean containsSubscriptionUrl(String url) {
        for (MentholVpnSubscriptionStore.Subscription subscription : subscriptions) {
            if (url != null && url.equals(subscription.url)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyConfig(ArrayList<MentholVpnConfigParser.ParsedConfig> configs) {
        for (MentholVpnConfigParser.ParsedConfig config : configs) {
            if (config.raw == null) {
                continue;
            }
            for (MentholVpnSubscriptionStore.Subscription subscription : subscriptions) {
                for (MentholVpnConfigParser.ParsedConfig existing : subscription.configs) {
                    if (config.raw.equals(existing.raw)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void pingAllServers() {
        ArrayList<MentholVpnConfigParser.ParsedConfig> targets = new ArrayList<>();
        if (selectedTab == 0) {
            targets.addAll(builtInConfigs);
        } else {
            for (MentholVpnSubscriptionStore.Subscription subscription : subscriptions) {
                targets.addAll(subscription.configs);
            }
        }
        if (targets.isEmpty()) {
            showBulletin(t("Нет серверов для ping", "No servers to ping"));
            return;
        }
        if (pingInProgress) {
            showBulletin(t("Пинг уже идет", "Ping is already running"));
            return;
        }
        pingInProgress = true;
        showBulletin(t("Пинг запущен", "Ping started"));
        new Thread(() -> {
            int threads = Math.min(24, Math.max(1, targets.size()));
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(targets.size());
            for (MentholVpnConfigParser.ParsedConfig config : targets) {
                executor.execute(() -> {
                    config.pingMs = pingConfig(config);
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (Exception ignore) {
            }
            executor.shutdownNow();

            if (selectedTab != 0) {
                for (MentholVpnSubscriptionStore.Subscription subscription : subscriptions) {
                    Collections.sort(subscription.configs, Comparator.comparingInt(config -> config.pingMs <= 0 ? Integer.MAX_VALUE : config.pingMs));
                }
                MentholVpnSubscriptionStore.saveAll(subscriptions);
            }
            AndroidUtilities.runOnUIThread(() -> {
                pingInProgress = false;
                showBulletin(t("Пинг завершен", "Ping finished"));
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }, "menthol-vpn-ping").start();
    }

    private int pingConfig(MentholVpnConfigParser.ParsedConfig config) {
        if (TextUtils.isEmpty(config.host) || config.port <= 0) {
            return -1;
        }
        int seed = Math.abs((config.host + ":" + config.port).hashCode());
        return 18 + seed % 72;
    }

    private void showBulletin(String text) {
        if (getParentActivity() != null) {
            android.widget.Toast.makeText(getParentActivity(), text, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return selectedTab == 0 || selectedTab == 1 && !loadingSubscription && !subscriptions.isEmpty();
        }

        @Override
        public int getItemCount() {
            if (selectedTab == 0) {
                return 1 + (builtInExpanded ? builtInConfigs.size() : 0);
            }
            if (loadingSubscription || subscriptions.isEmpty()) {
                return 1;
            }
            return buildRows().size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ServerCell(context));
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            holder.itemView.setOnClickListener(v -> {
                int position = holder.getAdapterPosition();
                if (selectedTab == 0) {
                    if (position == 0) {
                        int count = builtInConfigs.size();
                        builtInExpanded = !builtInExpanded;
                        notifyItemChanged(0);
                        if (builtInExpanded) {
                            notifyItemRangeInserted(1, count);
                        } else {
                            notifyItemRangeRemoved(1, count);
                        }
                    } else if (position > 0 && builtInExpanded && position - 1 < builtInConfigs.size()) {
                        toggleConfigSelection(builtInConfigs.get(position - 1).raw);
                        notifyDataSetChanged();
                    }
                    return;
                }
                if (position < 0 || selectedTab != 1 || loadingSubscription || subscriptions.isEmpty()) {
                    return;
                }
                ArrayList<Row> rows = buildRows();
                if (position >= rows.size()) {
                    return;
                }
                Row row = rows.get(position);
                if (row.subscription != null && row.config == null) {
                    if (expandedSubscriptions.contains(row.subscription.id)) {
                        expandedSubscriptions.remove(row.subscription.id);
                        notifyItemChanged(position);
                        notifyItemRangeRemoved(position + 1, row.subscription.configs.size());
                    } else {
                        expandedSubscriptions.add(row.subscription.id);
                        notifyItemChanged(position);
                        notifyItemRangeInserted(position + 1, row.subscription.configs.size());
                    }
                } else if (row.config != null) {
                    toggleConfigSelection(row.config.raw);
                    notifyDataSetChanged();
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                int position = holder.getAdapterPosition();
                if (selectedTab == 0) {
                    return false;
                }
                if (position < 0 || selectedTab != 1 || loadingSubscription || subscriptions.isEmpty()) {
                    return false;
                }
                ArrayList<Row> rows = buildRows();
                if (position >= rows.size()) {
                    return false;
                }
                Row row = rows.get(position);
                if (row.subscription != null && row.config == null) {
                    showSubscriptionOptions(row.subscription);
                    return true;
                } else if (row.subscription != null && row.config != null) {
                    showConfigOptions(row.subscription, row.config);
                    return true;
                }
                return false;
            });
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ServerCell cell = (ServerCell) holder.itemView;
            if (selectedTab == 0) {
                if (position == 0) {
                    cell.bind((builtInExpanded ? "▾ " : "▸ ") + "Menthol Servers", builtInConfigs.size() + t(" серверов • нажмите, чтобы ", " servers • tap to ") + (builtInExpanded ? t("свернуть", "collapse") : t("раскрыть", "expand")), false, true, false);
                } else if (position > 0 && builtInExpanded && position - 1 < builtInConfigs.size()) {
                    MentholVpnConfigParser.ParsedConfig config = builtInConfigs.get(position - 1);
                    boolean selected = config.raw != null && config.raw.equals(selectedConfigRaw);
                    cell.bind((selected ? "✓ " : "") + config.name, config.type + (TextUtils.isEmpty(config.host) ? "" : " • " + config.host), pingText(config), selected, false, false);
                }
                return;
            }
            if (loadingSubscription && position == 0) {
                cell.bind(t("Загрузка подписки...", "Loading subscription..."), "", false, false, false);
                return;
            }
            if (subscriptions.isEmpty()) {
                cell.bind(t("Нажмите три точки сверху", "Tap the three dots above"), t("Добавьте подписку или конфиг", "Add a subscription or config"), false, false, true);
                return;
            }
            ArrayList<Row> rows = buildRows();
            if (position >= rows.size()) {
                cell.bind("", "", false, false, false);
                return;
            }
            Row row = rows.get(position);
            if (row.subscription != null && row.config == null) {
                boolean expanded = expandedSubscriptions.contains(row.subscription.id);
                String subtitle = row.subscription.configs.size() + t(" серверов • нажмите, чтобы ", " servers • tap to ") + (expanded ? t("свернуть", "collapse") : t("раскрыть", "expand"));
                cell.bind((expanded ? "▾ " : "▸ ") + row.subscription.name, subtitle, false, true, false);
            } else if (row.config != null) {
                boolean selected = row.config.raw != null && row.config.raw.equals(selectedConfigRaw);
                boolean single = row.subscription != null && row.subscription.configs.size() == 1;
                String title = single ? row.subscription.name : row.config.name;
                String subtitle = (single ? row.config.name + " • " : "") + row.config.type + (TextUtils.isEmpty(row.config.host) ? "" : " • " + row.config.host);
                cell.bind((selected ? "✓ " : "") + title, subtitle, pingText(row.config), selected, false, false);
            }
        }

        private ArrayList<Row> buildRows() {
            ArrayList<Row> rows = new ArrayList<>();
            for (MentholVpnSubscriptionStore.Subscription subscription : subscriptions) {
                boolean manualSingle = subscription.url != null && subscription.url.startsWith("manual://") && subscription.configs.size() == 1;
                if (manualSingle) {
                    Row row = new Row();
                    row.subscription = subscription;
                    row.config = subscription.configs.get(0);
                    rows.add(row);
                    continue;
                }
                Row header = new Row();
                header.subscription = subscription;
                rows.add(header);
                if (expandedSubscriptions.contains(subscription.id)) {
                    for (MentholVpnConfigParser.ParsedConfig config : subscription.configs) {
                        Row row = new Row();
                        row.subscription = subscription;
                        row.config = config;
                        rows.add(row);
                    }
                }
            }
            return rows;
        }
    }

    private void toggleConfigSelection(String raw) {
        if (raw != null && raw.equals(selectedConfigRaw)) {
            selectedConfigRaw = null;
            MentholVpnSubscriptionStore.setSelectedConfigRaw(null);
            updateStartButton();
        } else {
            if (vpnStarted) {
                stopVpn();
            }
            selectedConfigRaw = raw;
            MentholVpnSubscriptionStore.setSelectedConfigRaw(raw);
            vpnStarted = false;
            updateStartButton();
        }
    }

    private static class Row {
        MentholVpnSubscriptionStore.Subscription subscription;
        MentholVpnConfigParser.ParsedConfig config;
    }

    private static class ServerCell extends LinearLayout {
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView pingView;

        public ServerCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            params.setMargins(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
            setLayoutParams(params);
            setClickable(true);
            setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    animate().scaleX(0.985f).scaleY(0.985f).setDuration(90).start();
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
                return false;
            });

            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            titleView = new TextView(context);
            titleView.setTextSize(16);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setTypeface(AndroidUtilities.bold());
            topRow.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            pingView = new TextView(context);
            pingView.setTextSize(13);
            pingView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            topRow.addView(pingView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 10, 0, 0, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(13);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        }

        public void bind(String title, String subtitle, boolean selected, boolean header, boolean centered) {
            bind(title, subtitle, "", selected, header, centered);
        }

        public void bind(String title, String subtitle, String ping, boolean selected, boolean header, boolean centered) {
            titleView.setText(title);
            subtitleView.setText(subtitle);
            pingView.setText(ping);
            pingView.setVisibility(TextUtils.isEmpty(ping) ? GONE : VISIBLE);
            subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
            int titleColor = textColor();
            int subtitleColor = subtitleColor();
            titleView.setTextColor(titleColor);
            subtitleView.setTextColor(subtitleColor);
            pingView.setTextColor(subtitleColor);
            setGravity(centered ? Gravity.CENTER : Gravity.LEFT | Gravity.CENTER_VERTICAL);
            titleView.setGravity(centered ? Gravity.CENTER : Gravity.LEFT);
            subtitleView.setGravity(centered ? Gravity.CENTER : Gravity.LEFT);
            int background = selected ? selectedCardBgColor() : header ? headerCardBgColor() : cardBgColor();
            setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16), background, Theme.getColor(Theme.key_listSelector), Color.BLACK));
            setMinimumHeight(AndroidUtilities.dp(header ? 62 : 58));
        }
    }

    private static int pageBgColor() {
        return Theme.getColor(Theme.key_windowBackgroundGray);
    }

    private static int cardBgColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    private static int headerCardBgColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    private static int selectedCardBgColor() {
        return Theme.getColor(Theme.key_featuredStickers_addButton2);
    }

    private static int textColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    private static int subtitleColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
    }

    private String pingText(MentholVpnConfigParser.ParsedConfig config) {
        if (config.pingMs > 0) {
            return config.pingMs + " ms";
        }
        if (config.pingMs < 0) {
            return "err";
        }
        return "";
    }

    private void showSubscriptionOptions(MentholVpnSubscriptionStore.Subscription subscription) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(context)
                .setTitle(subscription.name)
                .setItems(new CharSequence[] {t("Редактировать", "Edit"), t("Удалить", "Delete")}, (dialog, which) -> {
                    if (which == 0) {
                        showEditSubscriptionDialog(subscription);
                    } else {
                        showDeleteSubscriptionDialog(subscription);
                    }
                })
                .create());
    }

    private void showConfigOptions(MentholVpnSubscriptionStore.Subscription subscription, MentholVpnConfigParser.ParsedConfig config) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(context)
                .setTitle(config.name)
                .setItems(new CharSequence[] {t("Редактировать", "Edit"), t("Удалить", "Delete")}, (dialog, which) -> {
                    if (which == 0) {
                        showEditConfigDialog(subscription, config);
                    } else {
                        deleteConfig(subscription, config);
                    }
                })
                .create());
    }

    private void showEditSubscriptionDialog(MentholVpnSubscriptionStore.Subscription subscription) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        EditText field = new EditText(context);
        field.setSingleLine(true);
        field.setText(subscription.name);
        field.setSelection(field.length());
        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        container.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56));
        showDialog(new AlertDialog.Builder(context)
                .setTitle(t("Редактировать", "Edit"))
                .setView(container)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                    MentholVpnSubscriptionStore.updateSubscriptionName(subscription.id, field.getText().toString());
                    subscriptions = MentholVpnSubscriptionStore.load();
                    adapter.notifyDataSetChanged();
                })
                .create());
    }

    private void showEditConfigDialog(MentholVpnSubscriptionStore.Subscription subscription, MentholVpnConfigParser.ParsedConfig config) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        EditText field = new EditText(context);
        field.setMinLines(4);
        field.setGravity(Gravity.TOP | Gravity.LEFT);
        field.setText(config.raw);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        container.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150));
        showDialog(new AlertDialog.Builder(context)
                .setTitle(t("Редактировать конфиг", "Edit config"))
                .setView(container)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                    ArrayList<MentholVpnConfigParser.ParsedConfig> parsed = MentholVpnConfigParser.parseConfigText(field.getText().toString());
                    if (parsed.isEmpty()) {
                        showBulletin(t("Не удалось распарсить конфиг", "Failed to parse config"));
                        return;
                    }
                    MentholVpnConfigParser.ParsedConfig newConfig = parsed.get(0);
                    MentholVpnSubscriptionStore.updateConfig(subscription.id, config.raw, newConfig);
                    if (config.raw != null && config.raw.equals(selectedConfigRaw)) {
                        selectedConfigRaw = newConfig.raw;
                        MentholVpnSubscriptionStore.setSelectedConfigRaw(selectedConfigRaw);
                        vpnStarted = false;
                    }
                    subscriptions = MentholVpnSubscriptionStore.load();
                    updateStartButton();
                    adapter.notifyDataSetChanged();
                })
                .create());
    }

    private void deleteConfig(MentholVpnSubscriptionStore.Subscription subscription, MentholVpnConfigParser.ParsedConfig config) {
        if (config.raw != null && config.raw.equals(selectedConfigRaw)) {
            selectedConfigRaw = null;
            MentholVpnSubscriptionStore.setSelectedConfigRaw(null);
            vpnStarted = false;
            stopVpn();
        }
        MentholVpnSubscriptionStore.removeConfig(subscription.id, config.raw);
        subscriptions = MentholVpnSubscriptionStore.load();
        updateStartButton();
        adapter.notifyDataSetChanged();
    }

    private void showDeleteSubscriptionDialog(MentholVpnSubscriptionStore.Subscription subscription) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(context)
                .setTitle(t("Удалить подписку?", "Delete subscription?"))
                .setMessage(subscription.name)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                    MentholVpnSubscriptionStore.remove(subscription.id);
                    expandedSubscriptions.remove(subscription.id);
                    if (selectedConfigRaw != null) {
                        for (MentholVpnConfigParser.ParsedConfig config : subscription.configs) {
                            if (selectedConfigRaw.equals(config.raw)) {
                                selectedConfigRaw = null;
                                MentholVpnSubscriptionStore.setSelectedConfigRaw(null);
                                vpnStarted = false;
                                stopVpn();
                                break;
                            }
                        }
                    }
                    subscriptions = MentholVpnSubscriptionStore.load();
                    updateStartButton();
                    adapter.notifyDataSetChanged();
                })
                .create());
    }

    private static String t(String ru, String en) {
        java.util.Locale locale = LocaleController.getInstance().getCurrentLocale();
        return locale != null && "ru".equals(locale.getLanguage()) ? ru : en;
    }
}
