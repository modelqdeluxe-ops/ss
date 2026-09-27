.class Lhn/hato/ganadero/MainActivity$2;
.super Landroid/webkit/WebChromeClient;
.source "MainActivity.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lhn/hato/ganadero/MainActivity;->onCreate(Landroid/os/Bundle;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lhn/hato/ganadero/MainActivity;


# direct methods
.method constructor <init>(Lhn/hato/ganadero/MainActivity;)V
    .locals 0
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010
        }
        names = {
            null
        }
    .end annotation

    .line 88
    iput-object p1, p0, Lhn/hato/ganadero/MainActivity$2;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-direct {p0}, Landroid/webkit/WebChromeClient;-><init>()V

    return-void
.end method


# virtual methods
.method public onShowFileChooser(Landroid/webkit/WebView;Landroid/webkit/ValueCallback;Landroid/webkit/WebChromeClient$FileChooserParams;)Z
    .locals 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Landroid/webkit/WebView;",
            "Landroid/webkit/ValueCallback<",
            "[",
            "Landroid/net/Uri;",
            ">;",
            "Landroid/webkit/WebChromeClient$FileChooserParams;",
            ")Z"
        }
    .end annotation

    .line 91
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity$2;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-static {p1}, Lhn/hato/ganadero/MainActivity;->-$$Nest$fgetarchivoCb(Lhn/hato/ganadero/MainActivity;)Landroid/webkit/ValueCallback;

    move-result-object p1

    const/4 v2, 0x0

    if-eqz p1, :cond_0

    iget-object p1, p0, Lhn/hato/ganadero/MainActivity$2;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-static {p1}, Lhn/hato/ganadero/MainActivity;->-$$Nest$fgetarchivoCb(Lhn/hato/ganadero/MainActivity;)Landroid/webkit/ValueCallback;

    move-result-object p1

    invoke-interface {p1, v2}, Landroid/webkit/ValueCallback;->onReceiveValue(Ljava/lang/Object;)V

    .line 92
    :cond_0
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity$2;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-static {p1, p2}, Lhn/hato/ganadero/MainActivity;->-$$Nest$fputarchivoCb(Lhn/hato/ganadero/MainActivity;Landroid/webkit/ValueCallback;)V

    .line 93
    new-instance p1, Landroid/content/Intent;

    const-string p2, "android.intent.action.GET_CONTENT"

    invoke-direct {p1, p2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 94
    const-string p2, "android.intent.category.OPENABLE"

    invoke-virtual {p1, p2}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    .line 95
    const-string p2, "*/*"

    const-string v0, "Elegir respaldo de Rumentis"

    if-eqz p3, :cond_tipo

    invoke-virtual {p3}, Landroid/webkit/WebChromeClient$FileChooserParams;->getAcceptTypes()[Ljava/lang/String;

    move-result-object p3

    if-eqz p3, :cond_tipo

    array-length v1, p3

    if-lez v1, :cond_tipo

    const/4 v1, 0x0

    aget-object v1, p3, v1

    const-string v2, "image/*"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_tipo

    const-string p2, "image/*"

    const-string v0, "Elegir foto del animal"

    :cond_tipo
    const/4 p3, 0x0

    invoke-virtual {p1, p2}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    .line 97
    :try_start_0
    iget-object p2, p0, Lhn/hato/ganadero/MainActivity$2;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-static {p1, v0}, Landroid/content/Intent;->createChooser(Landroid/content/Intent;Ljava/lang/CharSequence;)Landroid/content/Intent;

    move-result-object p1

    const/16 v0, 0xb

    invoke-virtual {p2, p1, v0}, Lhn/hato/ganadero/MainActivity;->startActivityForResult(Landroid/content/Intent;I)V
    :try_end_0
    .catch Landroid/content/ActivityNotFoundException; {:try_start_0 .. :try_end_0} :catch_0

    const/4 p1, 0x1

    return p1

    .line 99
    :catch_0
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity$2;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-static {p1, p3}, Lhn/hato/ganadero/MainActivity;->-$$Nest$fputarchivoCb(Lhn/hato/ganadero/MainActivity;Landroid/webkit/ValueCallback;)V

    const/4 p1, 0x0

    return p1
.end method


.method public onPermissionRequest(Landroid/webkit/PermissionRequest;)V
    .locals 1

    iget-object v0, p0, Lhn/hato/ganadero/MainActivity$2;->this$0:Lhn/hato/ganadero/MainActivity;

    invoke-virtual {v0, p1}, Lhn/hato/ganadero/MainActivity;->pedirCamara(Landroid/webkit/PermissionRequest;)V

    return-void
.end method
