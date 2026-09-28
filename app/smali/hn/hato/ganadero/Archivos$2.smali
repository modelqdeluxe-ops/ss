.class Lhn/hato/ganadero/Archivos$2;
.super Ljava/lang/Object;
.source "Archivos.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lhn/hato/ganadero/Archivos;->abrir(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic val$a:Landroid/app/Activity;

.field final synthetic val$mime:Ljava/lang/String;

.field final synthetic val$n:Ljava/lang/String;


# direct methods
.method constructor <init>(Ljava/lang/String;Ljava/lang/String;Landroid/app/Activity;)V
    .registers 4
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .prologue
    .line 92
    iput-object p1, p0, Lhn/hato/ganadero/Archivos$2;->val$n:Ljava/lang/String;

    iput-object p2, p0, Lhn/hato/ganadero/Archivos$2;->val$mime:Ljava/lang/String;

    iput-object p3, p0, Lhn/hato/ganadero/Archivos$2;->val$a:Landroid/app/Activity;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .registers 6

    .prologue
    const/4 v4, 0x1

    .line 94
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "content://hn.hato.ganadero.archivos/"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    iget-object v1, p0, Lhn/hato/ganadero/Archivos$2;->val$n:Ljava/lang/String;

    invoke-static {v1}, Landroid/net/Uri;->encode(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v0

    .line 95
    new-instance v1, Landroid/content/Intent;

    const-string v2, "android.intent.action.VIEW"

    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 96
    iget-object v2, p0, Lhn/hato/ganadero/Archivos$2;->val$n:Ljava/lang/String;

    iget-object v3, p0, Lhn/hato/ganadero/Archivos$2;->val$mime:Ljava/lang/String;

    invoke-static {v2, v3}, Lhn/hato/ganadero/Archivos;->tipo(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v0, v2}, Landroid/content/Intent;->setDataAndType(Landroid/net/Uri;Ljava/lang/String;)Landroid/content/Intent;

    .line 97
    invoke-virtual {v1, v4}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 99
    :try_start_33
    iget-object v2, p0, Lhn/hato/ganadero/Archivos$2;->val$a:Landroid/app/Activity;

    invoke-virtual {v2, v1}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V
    :try_end_38
    .catch Ljava/lang/Exception; {:try_start_33 .. :try_end_38} :catch_39

    .line 114
    :goto_38
    return-void

    .line 100
    :catch_39
    move-exception v1

    .line 102
    :try_start_3a
    new-instance v1, Landroid/content/Intent;

    const-string v2, "android.intent.action.SEND"

    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 103
    iget-object v2, p0, Lhn/hato/ganadero/Archivos$2;->val$n:Ljava/lang/String;

    iget-object v3, p0, Lhn/hato/ganadero/Archivos$2;->val$mime:Ljava/lang/String;

    invoke-static {v2, v3}, Lhn/hato/ganadero/Archivos;->tipo(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    .line 104
    const-string v2, "android.intent.extra.STREAM"

    invoke-virtual {v1, v2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Landroid/os/Parcelable;)Landroid/content/Intent;

    .line 105
    iget-object v2, p0, Lhn/hato/ganadero/Archivos$2;->val$n:Ljava/lang/String;

    invoke-static {v2, v0}, Landroid/content/ClipData;->newRawUri(Ljava/lang/CharSequence;Landroid/net/Uri;)Landroid/content/ClipData;

    move-result-object v0

    invoke-virtual {v1, v0}, Landroid/content/Intent;->setClipData(Landroid/content/ClipData;)V

    .line 106
    const/4 v0, 0x1

    invoke-virtual {v1, v0}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 107
    const-string v0, "Abrir con"

    invoke-static {v1, v0}, Landroid/content/Intent;->createChooser(Landroid/content/Intent;Ljava/lang/CharSequence;)Landroid/content/Intent;

    move-result-object v0

    .line 108
    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 109
    iget-object v1, p0, Lhn/hato/ganadero/Archivos$2;->val$a:Landroid/app/Activity;

    invoke-virtual {v1, v0}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V
    :try_end_6d
    .catch Ljava/lang/Exception; {:try_start_3a .. :try_end_6d} :catch_6e

    goto :goto_38

    .line 110
    :catch_6e
    move-exception v0

    .line 111
    iget-object v0, p0, Lhn/hato/ganadero/Archivos$2;->val$a:Landroid/app/Activity;

    const-string v1, "No hay una app para abrir el archivo."

    invoke-static {v0, v1, v4}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object v0

    invoke-virtual {v0}, Landroid/widget/Toast;->show()V

    goto :goto_38
.end method
