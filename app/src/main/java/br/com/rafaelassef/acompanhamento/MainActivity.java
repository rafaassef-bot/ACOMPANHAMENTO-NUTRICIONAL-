package br.com.rafaelassef.acompanhamento;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.AtomicFile;
import android.util.Base64;
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
import androidx.core.content.FileProvider;
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
    private byte[] pdfPendente;
    private Uri fotoCamera;
    private File fotoCameraArquivo;

    /** Escolher um arquivo de backup para importar. */
    private final ActivityResultLauncher<Intent> escolherArquivo = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), resultado -> {
                Uri[] uris = WebChromeClient.FileChooserParams.parseResult(resultado.getResultCode(), resultado.getData());
                // Foto tirada na hora pela câmera: o arquivo vem no endereço que reservamos.
                if ((uris == null || uris.length == 0) && resultado.getResultCode() == RESULT_OK
                        && fotoCameraArquivo != null && fotoCameraArquivo.length() > 0) {
                    uris = new Uri[]{fotoCamera};
                }
                if (retornoArquivo != null) retornoArquivo.onReceiveValue(uris);
                retornoArquivo = null;
                fotoCamera = null;
                fotoCameraArquivo = null;
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

    /** Salvar o relatório em PDF onde o usuário escolher. */
    private final ActivityResultLauncher<String> salvarPdf = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
                String resultado;
                if (uri == null) {
                    resultado = "cancel";
                } else {
                    resultado = "erro";
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null && pdfPendente != null) {
                            os.write(pdfPendente);
                            resultado = "ok";
                        }
                    } catch (Exception ignored) { }
                }
                pdfPendente = null;
                executarJs("window.__pdfResult && window.__pdfResult('" + resultado + "')");
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
                String[] tipos = params.getAcceptTypes();
                boolean imagem = tipos != null && tipos.length > 0 && tipos[0] != null && tipos[0].startsWith("image");
                Intent abrir = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                abrir.addCategory(Intent.CATEGORY_OPENABLE);
                abrir.setType(imagem ? "image/*" : "*/*");
                Intent escolha = abrir;
                fotoCamera = null;
                fotoCameraArquivo = null;
                if (imagem) {
                    // Oferece a câmera junto com a galeria.
                    try {
                        File pasta = new File(getCacheDir(), "camera");
                        if (!pasta.exists()) pasta.mkdirs();
                        File[] antigas = pasta.listFiles();
                        if (antigas != null) for (File f : antigas) f.delete();
                        File arquivo = new File(pasta, "foto-" + System.currentTimeMillis() + ".jpg");
                        Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".arquivos", arquivo);
                        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        camera.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                        camera.setClipData(ClipData.newRawUri("foto", uri));
                        camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        escolha = Intent.createChooser(abrir, "Foto do corpo");
                        escolha.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                        fotoCamera = uri;
                        fotoCameraArquivo = arquivo;
                    } catch (Exception ignored) {
                        escolha = abrir;
                    }
                }
                try {
                    escolherArquivo.launch(escolha);
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

        private File arquivoFoto(String id) {
            if (id == null || !id.matches("[A-Za-z0-9]{4,40}")) return null;
            File pasta = new File(getFilesDir(), "fotos");
            if (!pasta.exists()) pasta.mkdirs();
            return new File(pasta, id + ".jpg");
        }

        /** Grava uma foto do corpo (JPEG em base64) num arquivo privado do app. */
        @JavascriptInterface
        public boolean savePhoto(String id, String base64) {
            File f = arquivoFoto(id);
            if (f == null) return false;
            AtomicFile af = new AtomicFile(f);
            FileOutputStream out = null;
            try {
                out = af.startWrite();
                out.write(Base64.decode(base64, Base64.DEFAULT));
                af.finishWrite(out);
                return true;
            } catch (Exception e) {
                if (out != null) af.failWrite(out);
                return false;
            }
        }

        /** Lê uma foto do corpo; devolve base64 ou "" se não existir. */
        @JavascriptInterface
        public String loadPhoto(String id) {
            File f = arquivoFoto(id);
            if (f == null || !f.exists()) return "";
            try (InputStream in = new AtomicFile(f).openRead()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] bloco = new byte[16384];
                int n;
                while ((n = in.read(bloco)) > 0) buf.write(bloco, 0, n);
                return Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void deletePhoto(String id) {
            File f = arquivoFoto(id);
            if (f != null) new AtomicFile(f).delete();
        }

        /** Abre o compartilhamento do Android (WhatsApp, e-mail...) com o relatório em PDF. */
        @JavascriptInterface
        public void sharePdf(String nomeArquivo, String base64) {
            runOnUiThread(() -> {
                try {
                    byte[] dados = Base64.decode(base64, Base64.DEFAULT);
                    File pasta = new File(getCacheDir(), "relatorios");
                    if (!pasta.exists() && !pasta.mkdirs()) throw new IllegalStateException("pasta");
                    File[] antigos = pasta.listFiles();
                    if (antigos != null) for (File f : antigos) f.delete();
                    File arquivo = new File(pasta, nomeArquivo.replaceAll("[^A-Za-z0-9._-]", "_"));
                    try (FileOutputStream out = new FileOutputStream(arquivo)) {
                        out.write(dados);
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".arquivos", arquivo);
                    Intent enviar = new Intent(Intent.ACTION_SEND);
                    enviar.setType("application/pdf");
                    enviar.putExtra(Intent.EXTRA_STREAM, uri);
                    enviar.putExtra(Intent.EXTRA_SUBJECT, "Relatório de evolução nutricional");
                    enviar.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(enviar, "Enviar relatório"));
                    executarJs("window.__pdfResult && window.__pdfResult('ok')");
                } catch (Exception e) {
                    executarJs("window.__pdfResult && window.__pdfResult('erro')");
                }
            });
        }

        /** Abre a tela do Android para escolher onde salvar o relatório em PDF. */
        @JavascriptInterface
        public void savePdf(String nomeArquivo, String base64) {
            try {
                pdfPendente = Base64.decode(base64, Base64.DEFAULT);
            } catch (Exception e) {
                executarJs("window.__pdfResult && window.__pdfResult('erro')");
                return;
            }
            runOnUiThread(() -> {
                try {
                    salvarPdf.launch(nomeArquivo);
                } catch (ActivityNotFoundException e) {
                    pdfPendente = null;
                    executarJs("window.__pdfResult && window.__pdfResult('erro')");
                }
            });
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
