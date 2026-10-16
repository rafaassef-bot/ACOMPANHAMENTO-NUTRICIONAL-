package br.com.rafaelassef.acompanhamento;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Acompanhamento Nutricional — Dr. Rafael Assef.
 *
 * O app é a mesma interface da versão web, empacotada num WebView.
 * Os prontuários ficam gravados num arquivo privado do app (pacientes.json),
 * que entra no backup automático do Android (conta Google do aparelho).
 */
public class MainActivity extends AppCompatActivity {

    private static final String ENDERECO_APP = "https://appassets.androidplatform.net/assets/index.html";
    private static final String ARQUIVO_DADOS = "pacientes.json";

    private WebView web;
    private ValueCallback<Uri[]> retornoArquivo;
    private String backupPendente;

    /** Escolher um arquivo de backup para importar. */
    private final ActivityResultLauncher<Intent> escolherArquivo = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), resultado -> {
                Uri[] uris = WebChromeClient.FileChooserParams.parseResult(resultado.getResultCode(), resultado.getData());
                if (retornoArquivo != null) retornoArquivo.onReceiveValue(uris);
                retornoArquivo = null;
            });

    /** Salvar o backup onde o usuário escolher (Downloads, Google Drive...). */
    private final ActivityResultLauncher<String> salvarBackup = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                String resultado;
                if (uri == null) {
                    resultado = "cancel";
                } else {
                    resultado = "erro";
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null && backupPendente != null) {
                            os.write(backupPendente.getBytes(StandardCharsets.UTF_8));
                            resultado = "ok";
                        }
                    } catch (Exception ignored) { }
                }
                backupPendente = null;
                executarJs("window.__backupResult && window.__backupResult('" + resultado + "')");
            });

    @Override
    protected void onCreate(Bundle estadoSalvo) {
        super.onCreate(estadoSalvo);

        // Tela de ponta a ponta, com o conteúdo afastado das barras do sistema e do teclado.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(ContextCompat.getColor(this, R.color.fundo));
        web = new WebView(this);
        raiz.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(raiz);
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        boolean noturno = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat barras = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        barras.setAppearanceLightStatusBars(!noturno);
        barras.setAppearanceLightNavigationBars(!noturno);

        // Os arquivos do app são servidos de um endereço local seguro.
        final WebViewAssetLoader carregador = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings ajustes = web.getSettings();
        ajustes.setJavaScriptEnabled(true);
        ajustes.setDomStorageEnabled(true);
        ajustes.setAllowFileAccess(false);
        ajustes.setAllowContentAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return carregador.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                // Links externos (telefone, e-mail, sites) abrem no app adequado.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> retorno, FileChooserParams params) {
                if (retornoArquivo != null) retornoArquivo.onReceiveValue(null);
                retornoArquivo = retorno;
                Intent abrir = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                abrir.addCategory(Intent.CATEGORY_OPENABLE);
                abrir.setType("*/*");
                try {
                    escolherArquivo.launch(abrir);
                } catch (ActivityNotFoundException e) {
                    retornoArquivo = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new PonteAndroid(), "AndroidApp");

        // Botão/gesto "voltar": fecha formulário ou volta para a lista antes de sair do app.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript("(window.__androidBack && window.__androidBack()) ? 1 : 0", valor -> {
                    if (!"1".equals(valor)) {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                        setEnabled(true);
                    }
                });
            }
        });

        if (estadoSalvo != null) {
            web.restoreState(estadoSalvo);
        } else {
            web.loadUrl(ENDERECO_APP);
        }
    }

    private void executarJs(String codigo) {
        runOnUiThread(() -> web.evaluateJavascript(codigo, null));
    }

    @Override
    protected void onSaveInstanceState(Bundle saida) {
        super.onSaveInstanceState(saida);
        web.saveState(saida);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** Funções que a interface (JavaScript) pode chamar. */
    private class PonteAndroid {
        private final Object trava = new Object();

        private AtomicFile arquivo() {
            return new AtomicFile(new File(getFilesDir(), ARQUIVO_DADOS));
        }

        /** Lê todos os pacientes gravados no aparelho (texto JSON) ou "" se ainda não houver nada. */
        @JavascriptInterface
        public String load() {
            synchronized (trava) {
                AtomicFile af = arquivo();
                if (!af.getBaseFile().exists()) return "";
                try (InputStream in = af.openRead()) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] bloco = new byte[16384];
                    int n;
                    while ((n = in.read(bloco)) > 0) buf.write(bloco, 0, n);
                    return buf.toString("UTF-8");
                } catch (Exception e) {
                    return "";
                }
            }
        }

        /** Grava todos os pacientes de forma segura (nunca deixa o arquivo pela metade). */
        @JavascriptInterface
        public boolean save(String json) {
            synchronized (trava) {
                AtomicFile af = arquivo();
                FileOutputStream out = null;
                try {
                    out = af.startWrite();
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    af.finishWrite(out);
                    return true;
                } catch (Exception e) {
                    if (out != null) af.failWrite(out);
                    return false;
                }
            }
        }

        /** Abre a tela do Android para escolher onde salvar o arquivo de backup. */
        @JavascriptInterface
        public void saveBackup(String nomeArquivo, String json) {
            backupPendente = json;
            runOnUiThread(() -> {
                try {
                    salvarBackup.launch(nomeArquivo);
                } catch (ActivityNotFoundException e) {
                    backupPendente = null;
                    executarJs("window.__backupResult && window.__backupResult('erro')");
                }
            });
        }
    }
}
