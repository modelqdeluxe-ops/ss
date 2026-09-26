.class Lhn/hato/ganadero/MainActivity$Puente;
.super Ljava/lang/Object;
.source "MainActivity.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lhn/hato/ganadero/MainActivity;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x2
    name = "Puente"
.end annotation


# instance fields
.field final synthetic this$0:Lhn/hato/ganadero/MainActivity;


# direct methods
.method public static synthetic $r8$lambda$OOrlRg7Oy18CWPhV3HVDkwcI4sM(Lhn/hato/ganadero/MainActivity$Puente;Ljava/lang/String;Ljava/lang/String;Z)V
    .locals 0

    invoke-direct {p0, p1, p2, p3}, Lhn/hato/ganadero/MainActivity$Puente;->lambda$barras$2(Ljava/lang/String;Ljava/lang/String;Z)V

    return-void
.end method

.method public static synthetic $r8$lambda$OWlKNTUIgjNBzzDpBGmRFRqd9y4(Lhn/hato/ganadero/MainActivity$Puente;)V
    .locals 0

    invoke-direct {p0}, Lhn/hato/ganadero/MainActivity$Puente;->lambda$escuchar$1()V

    return-void
.end method

.method public static synthetic $r8$lambda$dc23qxNQHs_ndm6WrwOfp596NtA(Lhn/hato/ganadero/MainActivity$Puente;Ljava/lang/String;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0, p1, p2}, Lhn/hato/ganadero/MainActivity$Puente;->lambda$guardar$0(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method private constructor <init>(Lhn/hato/ganadero/MainActivity;)V
    .locals 0
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x1010
        }
        names = {
            null
        }
    .end annotation

    .line 169
    iput-object p1, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method synthetic constructor <init>(Lhn/hato/ganadero/MainActivity;Lhn/hato/ganadero/MainActivity$Puente-IA;)V
    .locals 0

    invoke-direct {p0, p1}, Lhn/hato/ganadero/MainActivity$Puente;-><init>(Lhn/hato/ganadero/MainActivity;)V

    return-void
.end method

.method private synthetic lambda$barras$2(Ljava/lang/String;Ljava/lang/String;Z)V
    .locals 2

    .line 208
    :try_start_0
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-virtual {v0}, Lhn/hato/ganadero/MainActivity;->getWindow()Landroid/view/Window;

    move-result-object v0

    .line 209
    invoke-static {p1}, Lhn/hato/ganadero/MainActivity;->-$$Nest$smparse(Ljava/lang/String;)I

    move-result p1

    invoke-static {p2}, Lhn/hato/ganadero/MainActivity;->-$$Nest$smparse(Ljava/lang/String;)I

    move-result p2

    .line 210
    invoke-virtual {v0, p1}, Landroid/view/Window;->setStatusBarColor(I)V

    .line 211
    invoke-virtual {v0, p2}, Landroid/view/Window;->setNavigationBarColor(I)V

    .line 212
    iget-object p2, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-static {p2}, Lhn/hato/ganadero/MainActivity;->-$$Nest$fgetweb(Lhn/hato/ganadero/MainActivity;)Landroid/webkit/WebView;

    move-result-object p2

    invoke-virtual {p2, p1}, Landroid/webkit/WebView;->setBackgroundColor(I)V

    .line 213
    invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;

    move-result-object p1

    .line 214
    invoke-virtual {p1}, Landroid/view/View;->getSystemUiVisibility()I

    move-result p2

    .line 215
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x17

    if-lt v0, v1, :cond_1

    if-eqz p3, :cond_0

    and-int/lit16 p2, p2, -0x2001

    goto :goto_0

    :cond_0
    or-int/lit16 p2, p2, 0x2000

    .line 218
    :cond_1
    :goto_0
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1a

    if-lt v0, v1, :cond_3

    if-eqz p3, :cond_2

    and-int/lit8 p2, p2, -0x11

    goto :goto_1

    :cond_2
    or-int/lit8 p2, p2, 0x10

    .line 221
    :cond_3
    :goto_1
    invoke-virtual {p1, p2}, Landroid/view/View;->setSystemUiVisibility(I)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :catch_0
    return-void
.end method

.method private synthetic lambda$escuchar$1()V
    .locals 3

    .line 191
    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.speech.action.RECOGNIZE_SPEECH"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 192
    const-string v1, "android.speech.extra.LANGUAGE_MODEL"

    const-string v2, "free_form"

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 193
    const-string v1, "android.speech.extra.LANGUAGE"

    const-string v2, "es-HN"

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 194
    const-string v1, "android.speech.extra.PROMPT"

    const-string v2, "H\u00e1blale a Rumi"

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 195
    const-string v1, "android.speech.extra.PREFER_OFFLINE"

    const/4 v2, 0x1

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;

    .line 197
    :try_start_0
    iget-object v1, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    const/16 v2, 0xd

    invoke-virtual {v1, v0, v2}, Lhn/hato/ganadero/MainActivity;->startActivityForResult(Landroid/content/Intent;I)V
    :try_end_0
    .catch Landroid/content/ActivityNotFoundException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    .line 199
    :catch_0
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-static {v0}, Lhn/hato/ganadero/MainActivity;->-$$Nest$fgetweb(Lhn/hato/ganadero/MainActivity;)Landroid/webkit/WebView;

    move-result-object v0

    const-string v1, "window.toast&&toast(\'Tu tel\u00e9fono no tiene dictado por voz. Puedes escribirle a Rumi.\',4000)"

    const/4 v2, 0x0

    invoke-virtual {v0, v1, v2}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V

    :goto_0
    return-void
.end method

.method private synthetic lambda$guardar$0(Ljava/lang/String;Ljava/lang/String;)V
    .locals 1

    .line 173
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-static {v0, p1}, Lhn/hato/ganadero/MainActivity;->-$$Nest$fputpendienteDatos(Lhn/hato/ganadero/MainActivity;Ljava/lang/String;)V

    .line 174
    new-instance p1, Landroid/content/Intent;

    const-string v0, "android.intent.action.CREATE_DOCUMENT"

    invoke-direct {p1, v0}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 175
    const-string v0, "android.intent.category.OPENABLE"

    invoke-virtual {p1, v0}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    if-nez p2, :cond_0

    .line 176
    const-string p2, "hato.txt"

    .line 177
    :cond_0
    const-string v0, ".csv"

    invoke-virtual {p2, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_1

    const-string v0, "text/csv"

    goto :goto_0

    :cond_1
    const-string v0, ".json"

    invoke-virtual {p2, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_2

    const-string v0, "application/json"

    goto :goto_0

    :cond_2
    const-string v0, "text/plain"

    :goto_0
    invoke-virtual {p1, v0}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    .line 178
    const-string v0, "android.intent.extra.TITLE"

    invoke-virtual {p1, v0, p2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 180
    :try_start_0
    iget-object p2, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    const/16 v0, 0xc

    invoke-virtual {p2, p1, v0}, Lhn/hato/ganadero/MainActivity;->startActivityForResult(Landroid/content/Intent;I)V
    :try_end_0
    .catch Landroid/content/ActivityNotFoundException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_1

    .line 182
    :catch_0
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    const/4 p2, 0x0

    invoke-static {p1, p2}, Lhn/hato/ganadero/MainActivity;->-$$Nest$fputpendienteDatos(Lhn/hato/ganadero/MainActivity;Ljava/lang/String;)V

    .line 183
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-static {p1}, Lhn/hato/ganadero/MainActivity;->-$$Nest$fgetweb(Lhn/hato/ganadero/MainActivity;)Landroid/webkit/WebView;

    move-result-object p1

    const-string v0, "window.hatoGuardado&&window.hatoGuardado(false)"

    invoke-virtual {p1, v0, p2}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V

    :goto_1
    return-void
.end method


# virtual methods
.method public barras(Ljava/lang/String;Ljava/lang/String;Z)V
    .locals 2
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .line 206
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    new-instance v1, Lhn/hato/ganadero/MainActivity$Puente$$ExternalSyntheticLambda0;

    invoke-direct {v1, p0, p1, p2, p3}, Lhn/hato/ganadero/MainActivity$Puente$$ExternalSyntheticLambda0;-><init>(Lhn/hato/ganadero/MainActivity$Puente;Ljava/lang/String;Ljava/lang/String;Z)V

    invoke-virtual {v0, v1}, Lhn/hato/ganadero/MainActivity;->runOnUiThread(Ljava/lang/Runnable;)V

    return-void
.end method

.method public escuchar()V
    .locals 2
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .line 190
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    new-instance v1, Lhn/hato/ganadero/MainActivity$Puente$$ExternalSyntheticLambda1;

    invoke-direct {v1, p0}, Lhn/hato/ganadero/MainActivity$Puente$$ExternalSyntheticLambda1;-><init>(Lhn/hato/ganadero/MainActivity$Puente;)V

    invoke-virtual {v0, v1}, Lhn/hato/ganadero/MainActivity;->runOnUiThread(Ljava/lang/Runnable;)V

    return-void
.end method

.method public guardar(Ljava/lang/String;Ljava/lang/String;)V
    .locals 2
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .line 172
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity$Puente;->this$0:Lhn/hato/ganadero/MainActivity;

    new-instance v1, Lhn/hato/ganadero/MainActivity$Puente$$ExternalSyntheticLambda2;

    invoke-direct {v1, p0, p2, p1}, Lhn/hato/ganadero/MainActivity$Puente$$ExternalSyntheticLambda2;-><init>(Lhn/hato/ganadero/MainActivity$Puente;Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {v0, v1}, Lhn/hato/ganadero/MainActivity;->runOnUiThread(Ljava/lang/Runnable;)V

    return-void
.end method
