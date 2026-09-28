.class Lhn/hato/ganadero/Archivos$1;
.super Ljava/lang/Object;
.source "Archivos.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lhn/hato/ganadero/Archivos;->compartir(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic val$a:Landroid/app/Activity;

.field final synthetic val$mime:Ljava/lang/String;

.field final synthetic val$n:Ljava/lang/String;

.field final synthetic val$titulo:Ljava/lang/String;


# direct methods
.method constructor <init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/app/Activity;)V
    .registers 5
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .prologue
    .line 67
    iput-object p1, p0, Lhn/hato/ganadero/Archivos$1;->val$n:Ljava/lang/String;

    iput-object p2, p0, Lhn/hato/ganadero/Archivos$1;->val$mime:Ljava/lang/String;

    iput-object p3, p0, Lhn/hato/ganadero/Archivos$1;->val$titulo:Ljava/lang/String;

    iput-object p4, p0, Lhn/hato/ganadero/Archivos$1;->val$a:Landroid/app/Activity;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .registers 6

    .prologue
    const/4 v4, 0x1

    .line 70
    :try_start_1
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "content://hn.hato.ganadero.archivos/"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    iget-object v1, p0, Lhn/hato/ganadero/Archivos$1;->val$n:Ljava/lang/String;

    invoke-static {v1}, Landroid/net/Uri;->encode(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v1

    .line 71
    new-instance v2, Landroid/content/Intent;

    const-string v0, "android.intent.action.SEND"

    invoke-direct {v2, v0}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 72
    iget-object v0, p0, Lhn/hato/ganadero/Archivos$1;->val$n:Ljava/lang/String;

    iget-object v3, p0, Lhn/hato/ganadero/Archivos$1;->val$mime:Ljava/lang/String;

    invoke-static {v0, v3}, Lhn/hato/ganadero/Archivos;->tipo(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v2, v0}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    .line 73
    const-string v0, "android.intent.extra.STREAM"

    invoke-virtual {v2, v0, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Landroid/os/Parcelable;)Landroid/content/Intent;

    .line 74
    const-string v3, "android.intent.extra.SUBJECT"

    iget-object v0, p0, Lhn/hato/ganadero/Archivos$1;->val$titulo:Ljava/lang/String;

    if-nez v0, :cond_69

    iget-object v0, p0, Lhn/hato/ganadero/Archivos$1;->val$n:Ljava/lang/String;

    :goto_3d
    invoke-virtual {v2, v3, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 75
    iget-object v0, p0, Lhn/hato/ganadero/Archivos$1;->val$n:Ljava/lang/String;

    invoke-static {v0, v1}, Landroid/content/ClipData;->newRawUri(Ljava/lang/CharSequence;Landroid/net/Uri;)Landroid/content/ClipData;

    move-result-object v0

    invoke-virtual {v2, v0}, Landroid/content/Intent;->setClipData(Landroid/content/ClipData;)V

    .line 76
    const/4 v0, 0x1

    invoke-virtual {v2, v0}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 77
    iget-object v0, p0, Lhn/hato/ganadero/Archivos$1;->val$titulo:Ljava/lang/String;

    if-eqz v0, :cond_59

    iget-object v0, p0, Lhn/hato/ganadero/Archivos$1;->val$titulo:Ljava/lang/String;

    invoke-virtual {v0}, Ljava/lang/String;->length()I

    move-result v0

    if-nez v0, :cond_6c

    :cond_59
    const-string v0, "Compartir"

    :goto_5b
    invoke-static {v2, v0}, Landroid/content/Intent;->createChooser(Landroid/content/Intent;Ljava/lang/CharSequence;)Landroid/content/Intent;

    move-result-object v0

    .line 78
    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 79
    iget-object v1, p0, Lhn/hato/ganadero/Archivos$1;->val$a:Landroid/app/Activity;

    invoke-virtual {v1, v0}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V

    .line 83
    :goto_68
    return-void

    .line 74
    :cond_69
    iget-object v0, p0, Lhn/hato/ganadero/Archivos$1;->val$titulo:Ljava/lang/String;

    goto :goto_3d

    .line 77
    :cond_6c
    iget-object v0, p0, Lhn/hato/ganadero/Archivos$1;->val$titulo:Ljava/lang/String;
    :try_end_6e
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_6e} :catch_6f

    goto :goto_5b

    .line 80
    :catch_6f
    move-exception v0

    .line 81
    iget-object v0, p0, Lhn/hato/ganadero/Archivos$1;->val$a:Landroid/app/Activity;

    const-string v1, "No se pudo compartir el archivo."

    invoke-static {v0, v1, v4}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object v0

    invoke-virtual {v0}, Landroid/widget/Toast;->show()V

    goto :goto_68
.end method
