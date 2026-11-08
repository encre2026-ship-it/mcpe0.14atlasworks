package com.minis.atlas.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.minis.atlas.R;
import com.minis.atlas.core.Converter;
import com.minis.atlas.core.Img;
import com.minis.atlas.core.Pack;
import com.minis.atlas.core.Png;
import com.minis.atlas.core.Tga;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity implements Converter.Logger, Converter.Progress {
    static final int REQ_PICK = 1001, REQ_PERM = 1002, REQ_SAVE = 1003;

    final Handler ui = new Handler(Looper.getMainLooper());
    final StringBuilder logBuf = new StringBuilder();

    TextView tvDropName, tvDropInfo, tvPhase, tvPct, tvLog, tvResult, tvScaleVal, tvDesatVal;
    LinearLayout segScale, segMulti;
    View dropZone, cardProgress, cardResult, btnCopy, btnShare, btnPreview, btnZip;
    TextView btnStart;
    ProgressBar pb;
    SeekBar sbDesat;
    View logScroll;
    Switch swLoose, swMip, swMeta, swAssets;

    File zipFile;
    String zipLabel = "pack";
    int entries = 0;

    volatile boolean running = false, cancel = false;
    int scale = 1;
    String multi = "skip";
    int desat = 0;
    Store lastStore;
    Converter.Result lastResult;
    long startedAt;

    final Converter.Decoder DECODER = new Converter.Decoder() {
        @Override public Img decode(byte[] data, String name) throws Exception {
            if (Tga.looksLikeTga(data)) return Tga.decode(data);            return Png.decode(data);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        bind();
        buildScaleSeg();
        buildMultiSeg();
        wire();
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
        }
        Uri data = getIntent() != null ? getIntent().getData() : null;
        if (data != null) takeUri(data);
    }

    void bind() {
        tvDropName = (TextView) findViewById(R.id.dropTitle);
        tvDropInfo = (TextView) findViewById(R.id.dropSub);
        tvPhase = (TextView) findViewById(R.id.txtPhase);
        tvPct = (TextView) findViewById(R.id.txtPct);
        tvLog = (TextView) findViewById(R.id.txtLog);
        tvResult = (TextView) findViewById(R.id.txtResult);
        tvScaleVal = (TextView) findViewById(R.id.scaleVal);
        tvDesatVal = (TextView) findViewById(R.id.desatVal);
        segScale = (LinearLayout) findViewById(R.id.segScale);
        segMulti = (LinearLayout) findViewById(R.id.segMulti);
        dropZone = findViewById(R.id.dropZone);
        cardProgress = findViewById(R.id.cardProgress);
        cardResult = findViewById(R.id.cardResult);
        btnStart = (TextView) findViewById(R.id.btnStart);
        btnCopy = findViewById(R.id.btnCopy);
        btnShare = findViewById(R.id.btnShare);
        btnPreview = findViewById(R.id.btnPreview);
        btnZip = findViewById(R.id.btnZip);
        pb = (ProgressBar) findViewById(R.id.bar);
        sbDesat = (SeekBar) findViewById(R.id.seekDesat);
        logScroll = findViewById(R.id.logScroll);
        swLoose = (Switch) findViewById(R.id.swLoose);
        swMip = (Switch) findViewById(R.id.swMip);
        swMeta = (Switch) findViewById(R.id.swMeta);
        swAssets = (Switch) findViewById(R.id.swAssets);
        tvLog.setTypeface(Typeface.MONOSPACE);
    }

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    TextView seg(String text, boolean on) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(13), dp(8), dp(13), dp(8));
        t.setTextColor(on ? Color.parseColor("#E9EFF7") : Color.parseColor("#8FA0B5"));
        t.setBackgroundResource(on ? R.drawable.bg_seg_on : R.drawable.bg_seg);
        return t;
    }

    void buildScaleSeg() {
        segScale.removeAllViews();
        final int[] vals = {1, 2, 4};
        for (final int v : vals) {
            TextView t = seg(v + "×", v == scale);
            t.setOnClickListener(x -> {
                if (running) return;
                scale = v;
                buildScaleSeg();
                updateScaleHint();
            });
            segScale.addView(t);
        }
        updateScaleHint();
    }

    void updateScaleHint() {
        tvScaleVal.setText(String.format(Locale.US, "%d×%d", 512 * scale, 256 * scale));
    }

    void buildMultiSeg() {
        segMulti.removeAllViews();
        Object[][] defs = {
                {"skip", R.string.multi_skip},
                {"same", R.string.multi_same},
                {"variants", R.string.multi_alts},
        };
        for (Object[] d : defs) {
            final String key = (String) d[0];
            TextView t = seg(getString((Integer) d[1]), key.equals(multi));
            t.setOnClickListener(x -> {
                if (running) return;
                multi = key;
                buildMultiSeg();
            });
            segMulti.addView(t);
        }
    }

    void wire() {
        dropZone.setOnClickListener(v -> {
            if (running) return;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/zip", "application/x-zip-compressed", "application/octet-stream"});
            try {
                startActivityForResult(i, REQ_PICK);
            } catch (Exception e) {
                toast("没有可用的文件选择器");
            }
        });

        sbDesat.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                desat = p;
                tvDesatVal.setText(p + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });

        btnStart.setOnClickListener(v -> {
            if (running) {
                cancel = true;
                append("… 正在停止（当前贴图处理完后退出）", "#FBBF24");
                return;
            }
            if (zipFile == null) {
                toast("先选一个材质包");
                return;
            }
            start();
        });

        btnCopy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("log", logBuf.toString()));
            toast("日志已复制");
        });

        btnShare.setOnClickListener(v -> share());
        btnZip.setOnClickListener(v -> makeZip());
        btnPreview.setOnClickListener(v -> preview());
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (res != RESULT_OK || d == null || d.getData() == null) return;
        Uri u = d.getData();
        if (req == REQ_PICK) takeUri(u);
        else if (req == REQ_SAVE) saveZipTo(u);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == REQ_PERM && g.length > 0 && g[0] != PackageManager.PERMISSION_GRANTED) {
            toast("没有存储权限，结果将无法写入 Download 目录");
        }
    }

    void takeUri(final Uri uri) {
        setStartUi(false);
        tvDropName.setText("读取中…");
        tvDropInfo.setText("");
        new Thread(() -> {
            final File f = new File(getCacheDir(), "pack.zip");
            String name = "pack.zip";
            String err = null;
            try {
                String dn = displayName(uri);
                if (dn != null && !dn.isEmpty()) name = dn;
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new IllegalStateException("打不开这个文件");
                FileOutputStream fo = new FileOutputStream(f);
                try {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                } finally {
                    fo.close();
                    in.close();
                }
                int cnt = 0;
                try (ZipFile zf = new ZipFile(f)) {
                    cnt = zf.size();
                }
                final String nn = name;
                final int cc = cnt;
                final long len = f.length();
                zipFile = f;
                zipLabel = nn;
                entries = cc;
                ui.post(() -> {
                    tvDropName.setText(nn);
                    tvDropInfo.setText(String.format(Locale.US, "%.1f MB · %d 个条目 · 点这里可重新选择",
                            len / 1048576.0, cc));
                    setStartUi(true);
                    cardResult.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                err = e.getClass().getSimpleName() + ": " + e.getMessage();
                final String ee = err;
                ui.post(() -> {
                    tvDropName.setText(getString(R.string.pick_zip));
                    tvDropInfo.setText(getString(R.string.pick_zip_sub));
                    setStartUi(zipFile != null);
                    toast("读取失败 " + ee);
                });
            }
        }, "pick").start();
    }

    String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignore) { }
        String p = uri.getLastPathSegment();
        return p == null ? null : p.substring(p.lastIndexOf('/') + 1);
    }

    void setStartUi(boolean enabled) {
        btnStart.setEnabled(enabled);
        btnStart.setBackgroundResource(enabled ? R.drawable.bg_btn_primary : R.drawable.bg_btn_primary_off);
        btnStart.setAlpha(enabled ? 1f : 0.55f);
    }

    void start() {
        if (running) return;
        running = true;
        cancel = false;
        logBuf.setLength(0);
        tvLog.setText("");
        pb.setProgress(0);
        tvPct.setText("0%");
        tvPhase.setText(R.string.phase_idle);
        cardProgress.setVisibility(View.VISIBLE);
        cardResult.setVisibility(View.GONE);
        btnStart.setText("停止");
        setStartUi(true);
        startedAt = System.currentTimeMillis();
        final String label = zipLabel;
        final int sc = scale;
        final String mu = multi;
        final int ds = desat;
        final boolean lo = swLoose.isChecked(), mi = swMip.isChecked(),
                me = swMeta.isChecked(), as = swAssets.isChecked();
        new Thread(() -> {
            Store store = null;
            Pack pack = null;
            String err = null;
            try {
                String base = label;
                int dot = base.lastIndexOf('.');
                if (dot > 0) base = base.substring(0, dot);
                store = new Store(this, base + "_" + sc + "x");
                store.clearOld();
                append("输出目录: " + store.pathHint(), "#8FA0B5");

                Refs refs = Refs.load(this);
                append("参考图集: terrain " + refs.terrain.w + "×" + refs.terrain.h
                        + " / items " + refs.items.w + "×" + refs.items.h, "#8FA0B5");

                pack = new Pack(zipFile);
                Converter.Opt o = new Converter.Opt();
                o.scale = sc;
                o.multi = mu;
                o.desat = ds;
                o.loose = lo;
                o.emitMip = mi;
                o.emitMeta = me;
                o.emitAssets = as;

                Converter.Result r = Converter.run(o, pack, label,
                        refs.terrain, refs.tMeta, refs.items, refs.iMeta,
                        DECODER, Png::encode, store, this, this);

                lastStore = store;
                lastResult = r;
                final boolean cancelled = r.cancelled;
                final String summary = summary(r, store);
                final String outDir = store.pathHint();
                ui.post(() -> {
                    running = false;
                    btnStart.setText(getString(R.string.btn_start));
                    setStartUi(true);
                    if (cancelled) {
                        tvPhase.setText("已停止");
                        append("已取消，输出保留在 " + outDir, "#FBBF24");
                    } else {
                        tvPhase.setText("完成");
                        tvPct.setText("100%");
                        pb.setProgress(1000);
                        append("完成，用时 " + ((System.currentTimeMillis() - startedAt) / 1000)
                                + " 秒", "#34D399");
                        tvResult.setText(summary);
                        cardResult.setVisibility(View.VISIBLE);
                        cardResult.requestFocus();
                    }
                });
            } catch (OutOfMemoryError e) {
                err = "内存不足：把倍率调到 1× 再试，或先关闭「生成 mip」";
            } catch (Exception e) {
                err = e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                running = false;
                if (pack != null) try { pack.close(); } catch (Exception ignore) { }
            }
            if (err != null) {
                final String ee = err;
                ui.post(() -> {
                    btnStart.setText(getString(R.string.btn_start));
                    setStartUi(true);
                    tvPhase.setText("失败");
                    append("失败: " + ee, "#F87171");
                    toast(ee);
                });
            }
        }, "convert").start();
    }

    String summary(Converter.Result r, Store s) {
        StringBuilder b = new StringBuilder();
        if (r.terrainTotal > 0) {
            b.append(String.format(Locale.US, "方块图集 %d×%d　对上 %d/%d (%.0f%%)\n",
                    r.terrainW, r.terrainH, r.terrainMatched, r.terrainTotal,
                    100.0 * r.terrainMatched / Math.max(1, r.terrainTotal)));
        }
        if (r.itemsW > 0) {
            b.append(String.format(Locale.US, "物品图集 %d×%d　替换 %d 名 / %d 格　保留原版 %d 名\n",
                    r.itemsW, r.itemsH, r.itemsFilled, r.itemsTiles, r.itemsKept));
        }
        b.append(String.format(Locale.US, "输出 %d 个文件 · %.1f MB\n", s.count(), s.bytes() / 1048576.0));
        b.append(s.pathHint());
        return b.toString();
    }

    @Override
    public void log(String line) {
        append(line, colorOf(line));
    }

    @Override
    public boolean cancelled() {
        return cancel;
    }

    @Override
    public void onProgress(final int percent, final String stage) {
        ui.post(() -> {
            pb.setProgress(percent * 10);
            tvPct.setText(percent + "%");
            tvPhase.setText(stage);
        });
    }

    static String colorOf(String line) {
        if (line.contains("失败") || line.contains("错误") || line.startsWith("  没对上")) return "#FBBF24";
        if (line.startsWith("[")) return "#7FB3FF";
        if (line.contains("完成")) return "#34D399";
        return "#B9C7DA";
    }

    void append(final String line, final String color) {
        logBuf.append(line).append('\n');
        if (logBuf.length() > 400000) logBuf.delete(0, 200000);
        ui.post(() -> {
            int start = tvLog.length();
            tvLog.append(line + "\n");
            if (color != null && tvLog.length() < 300000) {
                SpannableStringBuilder sb = new SpannableStringBuilder(tvLog.getText());
                sb.setSpan(new ForegroundColorSpan(Color.parseColor(color)), start,
                        sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tvLog.setText(sb);
            }
            if (logScroll instanceof ScrollView) {
                final ScrollView sv = (ScrollView) logScroll;
                sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    void toast(final String m) {
        ui.post(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    void share() {
        if (lastStore == null) return;
        List<Uri> all = lastStore.allUris();
        if (all.isEmpty()) {
            toast("还没有可分享的文件");
            return;
        }
        Intent i;
        if (all.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.setType(Store.mime(lastStore.names().isEmpty() ? "x.png" : lastStore.names().get(0)));
            i.putExtra(Intent.EXTRA_STREAM, all.get(0));
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.setType("*/*");
            ArrayList<Uri> part = new ArrayList<>(all.subList(0, Math.min(400, all.size())));
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, part);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, getString(R.string.btn_share)));
        } catch (Exception e) {
            toast("没有可用的分享应用");
        }
    }

    File zipCache;

    void makeZip() {
        if (lastStore == null) {
            toast("还没有结果");
            return;
        }
        btnZip.setEnabled(false);
        toast("正在打包…");
        new Thread(() -> {
            String err = null;
            try {
                File f = new File(getCacheDir(), "atlas-" + System.currentTimeMillis() + ".zip");
                ZipOutputStream zo = new ZipOutputStream(new java.io.BufferedOutputStream(
                        new FileOutputStream(f), 1 << 16));
                try {
                    for (String rel : lastStore.names()) {
                        byte[] d = lastStore.read(rel);
                        if (d == null) continue;
                        zo.putNextEntry(new ZipEntry(rel));
                        zo.write(d);
                        zo.closeEntry();
                    }
                } finally {
                    zo.close();
                }
                zipCache = f;
                ui.post(() -> {
                    btnZip.setEnabled(true);
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/zip");
                    i.putExtra(Intent.EXTRA_TITLE, "atlas-" + Math.max(1, scale) + "x.zip");
                    startActivityForResult(i, REQ_SAVE);
                });
            } catch (Exception e) {
                err = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            if (err != null) {
                final String ee = err;
                ui.post(() -> {
                    btnZip.setEnabled(true);
                    toast("打包失败 " + ee);
                });
            }
        }, "zip").start();
    }

    void saveZipTo(Uri dst) {
        if (zipCache == null || !zipCache.exists()) return;
        try {
            OutputStream os = getContentResolver().openOutputStream(dst);
            if (os == null) throw new IllegalStateException("无法写入");
            InputStream in = new java.io.FileInputStream(zipCache);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            } finally {
                in.close();
                os.close();
            }
            toast("已保存");
        } catch (Exception e) {
            toast("保存失败 " + e.getMessage());
        }
    }

    void preview() {
        if (lastStore == null) {
            toast("还没有结果");
            return;
        }
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0A0D13"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        sv.addView(box);

        addPreview(box, "terrain-atlas.png", "方块图集");
        addPreview(box, "items-opaque.png", "物品图集");

        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_preview)
                .setView(sv)
                .setPositiveButton("关闭", null)
                .show();
    }

    void addPreview(LinearLayout box, String rel, String title) {
        TextView t = new TextView(this);
        t.setText(title + "  (" + rel + ")");
        t.setTextColor(Color.parseColor("#8FA0B5"));
        t.setTextSize(12);
        t.setPadding(0, dp(8), 0, dp(6));
        box.addView(t);
        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(Color.parseColor("#1A2230"));
        byte[] d = lastStore.read(rel);
        if (d == null) {
            t.setText(title + "  (未生成)");
            return;
        }
        BitmapFactory.Options op = new BitmapFactory.Options();
        op.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, op);
        int sample = 1;
        while (op.outWidth / sample > 2048 || op.outHeight / sample > 2048) sample *= 2;
        op.inJustDecodeBounds = false;
        op.inSampleSize = sample;
        op.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length, op);
        if (bm == null) {
            t.setText(title + "  (解码失败)");
            return;
        }
        iv.setImageBitmap(bm);
        int w = getResources().getDisplayMetrics().widthPixels - dp(48);
        iv.setLayoutParams(new LinearLayout.LayoutParams(w,
                Math.max(dp(80), w * bm.getHeight() / Math.max(1, bm.getWidth()))));
        box.addView(iv);
    }
}
