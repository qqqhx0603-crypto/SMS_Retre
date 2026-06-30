package com.smsretre.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private ConfigStore configStore;
    private SmsDatabase database;
    private ExecutorService executor;

    private Switch enabledSwitch;
    private EditText senderEmailInput;
    private EditText authCodeInput;
    private EditText recipientEmailInput;
    private EditText sim1LabelInput;
    private EditText sim2LabelInput;
    private TextView statusView;
    private TextView recordsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configStore = new ConfigStore(this);
        database = new SmsDatabase(this);
        executor = Executors.newSingleThreadExecutor();
        buildUi();
        loadConfig();
        requestRuntimePermissions();
        BatteryCheckJobService.schedulePeriodic(this);
        BatteryAlertManager.checkAndEnqueue(this);
        refreshRecords();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecords();
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

        TextView title = text("SMS-Retre", 24, true);
        root.addView(title);

        TextView packageName = text("包名：" + getPackageName(), 14, false);
        packageName.setPadding(0, dp(6), 0, dp(12));
        root.addView(packageName);

        enabledSwitch = new Switch(this);
        enabledSwitch.setText("启用短信转发");
        enabledSwitch.setTextSize(16);
        enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onEnabledChanged(buttonView, isChecked);
            }
        });
        root.addView(enabledSwitch);

        senderEmailInput = input("发件 QQ 邮箱");
        senderEmailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(label("发件 QQ 邮箱"));
        root.addView(senderEmailInput);

        authCodeInput = input("QQ 邮箱 SMTP 授权码，留空表示不修改");
        authCodeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(label("SMTP 授权码"));
        root.addView(authCodeInput);

        recipientEmailInput = input("收件邮箱");
        recipientEmailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(label("收件邮箱"));
        root.addView(recipientEmailInput);

        sim1LabelInput = input("SIM1 接收号码或备注，例如 +447xxxx");
        root.addView(label("SIM1 接收号码/备注"));
        root.addView(sim1LabelInput);

        sim2LabelInput = input("SIM2 接收号码或备注，可留空");
        root.addView(label("SIM2 接收号码/备注"));
        root.addView(sim2LabelInput);

        Button saveButton = button("保存配置");
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveConfig();
            }
        });
        root.addView(saveButton);

        Button testButton = button("测试发送");
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testSend();
            }
        });
        root.addView(testButton);

        Button retryButton = button("手动重试失败记录");
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                retryFailures();
            }
        });
        root.addView(retryButton);

        Button notificationAccessButton = button("打开通知访问设置");
        notificationAccessButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openNotificationAccessSettings();
            }
        });
        root.addView(notificationAccessButton);

        Button refreshButton = button("刷新记录");
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshRecords();
            }
        });
        root.addView(refreshButton);

        statusView = text("", 14, false);
        statusView.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusView);

        recordsView = text("", 14, false);
        recordsView.setTextIsSelectable(true);
        root.addView(recordsView);

        setContentView(scrollView);
    }

    private void onEnabledChanged(CompoundButton button, boolean checked) {
        setStatus(checked ? "已打开开关，保存配置后生效。" : "已关闭开关，保存配置后生效。");
    }

    private void loadConfig() {
        MailConfig config = configStore.load();
        enabledSwitch.setChecked(config.enabled);
        senderEmailInput.setText(config.senderEmail);
        recipientEmailInput.setText(config.recipientEmail);
        sim1LabelInput.setText(config.sim1Label);
        sim2LabelInput.setText(config.sim2Label);
        authCodeInput.setText("");
        setStatus(configStore.hasAuthCode() ? "已保存授权码。" : "尚未保存授权码。");
    }

    private void saveConfig() {
        try {
            configStore.save(
                    enabledSwitch.isChecked(),
                    senderEmailInput.getText().toString(),
                    recipientEmailInput.getText().toString(),
                    authCodeInput.getText().toString(),
                    sim1LabelInput.getText().toString(),
                    sim2LabelInput.getText().toString()
            );
            authCodeInput.setText("");
            MailConfig config = configStore.load();
            String suffix = config.isComplete() ? "配置完整。" : "配置未完整，请检查邮箱和授权码。";
            setStatus("已保存。" + suffix);
            BatteryCheckJobService.schedulePeriodic(this);
            BatteryAlertManager.checkAndEnqueue(this);
        } catch (Exception e) {
            setStatus("保存失败：" + messageOf(e));
        }
    }

    private void testSend() {
        saveConfig();
        MailConfig config = configStore.load();
        if (!config.isComplete()) {
            setStatus("测试失败：邮箱配置不完整。");
            return;
        }
        setStatus("正在发送测试邮件...");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    new MailSender().send(
                            config,
                            "SMS-Retre 测试邮件 " + TimeUtils.compact(System.currentTimeMillis()),
                            "这是一封 SMS-Retre 测试邮件。\n\n如果你能收到，说明 QQ 邮箱 SMTP 配置可用。"
                    );
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setStatus("测试邮件发送成功。");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setStatus("测试邮件发送失败：" + messageOf(e));
                        }
                    });
                }
            }
        });
    }

    private void retryFailures() {
        long now = System.currentTimeMillis();
        int count = database.resetFailuresToPending(now);
        if (count > 0) {
            SmsForwardService.start(this);
            setStatus(String.format(Locale.US, "已重新加入 %d 条失败记录。", count));
        } else {
            setStatus("没有可重试的失败记录。");
        }
        refreshRecords();
    }

    private void refreshRecords() {
        int pending = database.countByStatus(SmsRecord.STATUS_PENDING);
        int failed = database.countByStatus(SmsRecord.STATUS_FAILED);
        List<SmsRecord> records = database.getRecent(30);

        StringBuilder builder = new StringBuilder();
        builder.append("待发送：").append(pending)
                .append("    失败：").append(failed)
                .append("\n通知兜底：").append(notificationAccessLabel())
                .append("\n\n最近记录：\n");
        if (records.isEmpty()) {
            builder.append("暂无记录。");
        } else {
            for (SmsRecord record : records) {
                appendRecord(builder, record);
            }
        }
        recordsView.setText(builder.toString());
    }

    private void appendRecord(StringBuilder builder, SmsRecord record) {
        builder.append("#").append(record.id)
                .append("  ").append(statusLabel(record.status))
                .append("  ").append(TimeUtils.full(record.receivedAt))
                .append("\n类型：").append(typeLabel(record.recordType))
                .append("\n来源：").append(record.sender)
                .append("\n尝试：").append(record.attemptCount).append(" 次");
        if (SmsRecord.TYPE_SMS.equals(record.recordType)) {
            builder.append("\n接收：").append(receiverLabel(record));
        }
        if (SmsRecord.TYPE_BATTERY.equals(record.recordType)) {
            builder.append("\n电量：").append(record.batteryLevel).append("%");
        }
        if (record.firstAttemptAt > 0L && SmsRecord.STATUS_PENDING.equals(record.status)) {
            builder.append("，已过 ")
                    .append(TimeUtils.durationSince(record.firstAttemptAt, System.currentTimeMillis()));
        }
        if (record.lastError != null && !record.lastError.isEmpty()) {
            builder.append("\n失败原因：").append(record.lastError);
        }
        builder.append("\n内容：").append(preview(record.body)).append("\n\n");
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        ArrayList<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECEIVE_SMS);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), 1001);
        }
    }

    private void openNotificationAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            setStatus("打开通知访问设置失败：" + messageOf(e));
        }
    }

    private TextView label(String value) {
        TextView textView = text(value, 13, true);
        textView.setPadding(0, dp(14), 0, dp(4));
        return textView;
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        editText.setTextSize(16);
        return editText;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setPadding(0, dp(6), 0, dp(6));
        return button;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return textView;
    }

    private void setStatus(String message) {
        statusView.setText(message);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String statusLabel(String status) {
        if (SmsRecord.STATUS_SENT.equals(status)) {
            return "已发送";
        }
        if (SmsRecord.STATUS_FAILED.equals(status)) {
            return "失败";
        }
        return "待发送";
    }

    private static String typeLabel(String type) {
        if (SmsRecord.TYPE_BATTERY.equals(type)) {
            return "电量提醒";
        }
        return "短信";
    }

    private static String receiverLabel(SmsRecord record) {
        String label = record.receiverLabel == null || record.receiverLabel.trim().isEmpty()
                ? "unknown"
                : record.receiverLabel.trim();
        String slot = record.receiverSlot < 0 ? "slot unknown" : "SIM" + (record.receiverSlot + 1);
        String subId = record.receiverSubId < 0 ? "subId unknown" : "subId " + record.receiverSubId;
        return label + " / " + slot + " / " + subId;
    }

    private String notificationAccessLabel() {
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabledListeners == null || enabledListeners.trim().isEmpty()) {
            return "未开启";
        }
        return enabledListeners.toLowerCase(Locale.US).contains(getPackageName().toLowerCase(Locale.US))
                ? "已开启"
                : "未开启";
    }

    private static String preview(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.length() <= 120) {
            return cleaned;
        }
        return cleaned.substring(0, 120) + "...";
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
