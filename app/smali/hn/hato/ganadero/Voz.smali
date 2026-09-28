.class public final Lhn/hato/ganadero/Voz;
.super Ljava/lang/Object;
.source "Voz.java"

# interfaces
.implements Landroid/speech/tts/TextToSpeech$OnInitListener;


# static fields
.field public static idioma:Ljava/lang/String;

.field private static uno:Lhn/hato/ganadero/Voz;


# instance fields
.field private final ctx:Landroid/content/Context;

.field private elegida:Ljava/lang/String;

.field private lengua:Ljava/lang/String;

.field private listo:Z

.field private pendVoz:Ljava/lang/String;

.field private pendiente:Ljava/lang/String;

.field private tts:Landroid/speech/tts/TextToSpeech;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .prologue
    .line 15
    const-string v0, "es"

    sput-object v0, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    return-void
.end method

.method private constructor <init>(Landroid/content/Context;)V
    .locals 2

    .prologue
    .line 23
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    invoke-virtual {p1}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    iput-object v0, p0, Lhn/hato/ganadero/Voz;->ctx:Landroid/content/Context;

    new-instance v0, Landroid/speech/tts/TextToSpeech;

    iget-object v1, p0, Lhn/hato/ganadero/Voz;->ctx:Landroid/content/Context;

    invoke-direct {v0, v1, p0}, Landroid/speech/tts/TextToSpeech;-><init>(Landroid/content/Context;Landroid/speech/tts/TextToSpeech$OnInitListener;)V

    iput-object v0, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    return-void
.end method

.method static synthetic access$000(Landroid/speech/tts/Voice;)I
    .locals 1

    .prologue
    .line 12
    invoke-static {p0}, Lhn/hato/ganadero/Voz;->puntaje(Landroid/speech/tts/Voice;)I

    move-result v0

    return v0
.end method

.method public static ajustes(Landroid/content/Context;)V
    .locals 2

    .prologue
    .line 148
    :try_start_0
    new-instance v0, Landroid/content/Intent;

    const-string v1, "com.android.settings.TTS_SETTINGS"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 149
    const/high16 v1, 0x10000000

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 150
    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 158
    :goto_0
    return-void

    .line 151
    :catch_0
    move-exception v0

    .line 153
    :try_start_1
    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.speech.tts.engine.INSTALL_TTS_DATA"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 154
    const/high16 v1, 0x10000000

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 155
    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_0

    .line 156
    :catch_1
    move-exception v0

    goto :goto_0
.end method

.method private buscar(Ljava/lang/String;)Landroid/speech/tts/Voice;
    .locals 5

    .prologue
    const/4 v3, 0x0

    .line 65
    :try_start_0
    iget-object v0, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v0}, Landroid/speech/tts/TextToSpeech;->getVoices()Ljava/util/Set;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    move-result-object v0

    .line 66
    if-nez v0, :cond_1

    .line 73
    :cond_0
    :goto_0
    return-object v3

    .line 67
    :cond_1
    const/4 v2, -0x1

    .line 68
    invoke-interface {v0}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v4

    :goto_1
    invoke-interface {v4}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_0

    invoke-interface {v4}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/speech/tts/Voice;

    .line 69
    if-eqz p1, :cond_2

    invoke-virtual {v0}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2

    move-object v3, v0

    goto :goto_0

    .line 70
    :cond_2
    invoke-static {v0}, Lhn/hato/ganadero/Voz;->puntaje(Landroid/speech/tts/Voice;)I

    move-result v1

    .line 71
    if-le v1, v2, :cond_3

    :goto_2
    move v2, v1

    move-object v3, v0

    .line 72
    goto :goto_1

    .line 65
    :catch_0
    move-exception v0

    goto :goto_0

    :cond_3
    move v1, v2

    move-object v0, v3

    goto :goto_2
.end method

.method public static callar(Landroid/content/Context;)V
    .locals 2

    .prologue
    .line 110
    invoke-static {p0}, Lhn/hato/ganadero/Voz;->de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;

    move-result-object v1

    .line 111
    monitor-enter v1

    const/4 v0, 0x0

    :try_start_0
    iput-object v0, v1, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    :try_start_1
    iget-object v0, v1, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v0}, Landroid/speech/tts/TextToSpeech;->stop()I
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    :goto_0
    :try_start_2
    monitor-exit v1

    .line 112
    return-void

    .line 111
    :catchall_0
    move-exception v0

    monitor-exit v1
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    throw v0

    :catch_0
    move-exception v0

    goto :goto_0
.end method

.method private static declared-synchronized de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;
    .locals 2

    .prologue
    .line 25
    const-class v1, Lhn/hato/ganadero/Voz;

    monitor-enter v1

    :try_start_0
    sget-object v0, Lhn/hato/ganadero/Voz;->uno:Lhn/hato/ganadero/Voz;

    if-nez v0, :cond_0

    new-instance v0, Lhn/hato/ganadero/Voz;

    invoke-direct {v0, p0}, Lhn/hato/ganadero/Voz;-><init>(Landroid/content/Context;)V

    sput-object v0, Lhn/hato/ganadero/Voz;->uno:Lhn/hato/ganadero/Voz;

    :cond_0
    sget-object v0, Lhn/hato/ganadero/Voz;->uno:Lhn/hato/ganadero/Voz;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit v1

    return-object v0

    :catchall_0
    move-exception v0

    monitor-exit v1

    throw v0
.end method

.method private declared-synchronized decir(Ljava/lang/String;Ljava/lang/String;)V
    .locals 9

    .prologue
    const/16 v0, 0xf3c

    const/4 v2, 0x0

    .line 77
    monitor-enter p0

    :try_start_0
    iget-boolean v1, p0, Lhn/hato/ganadero/Voz;->listo:Z

    if-nez v1, :cond_1

    iput-object p1, p0, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;

    iput-object p2, p0, Lhn/hato/ganadero/Voz;->pendVoz:Ljava/lang/String;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 102
    :cond_0
    :goto_0
    monitor-exit p0

    return-void

    .line 79
    :cond_1
    :try_start_1
    invoke-direct {p0}, Lhn/hato/ganadero/Voz;->lengua()V

    .line 80
    invoke-direct {p0, p2}, Lhn/hato/ganadero/Voz;->buscar(Ljava/lang/String;)Landroid/speech/tts/Voice;

    move-result-object v1

    .line 81
    if-eqz v1, :cond_3

    iget-object v3, p0, Lhn/hato/ganadero/Voz;->elegida:Ljava/lang/String;

    if-eqz v3, :cond_2

    iget-object v3, p0, Lhn/hato/ganadero/Voz;->elegida:Ljava/lang/String;

    invoke-virtual {v1}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-nez v3, :cond_3

    :cond_2
    iget-object v3, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v3, v1}, Landroid/speech/tts/TextToSpeech;->setVoice(Landroid/speech/tts/Voice;)I

    invoke-virtual {v1}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v1

    iput-object v1, p0, Lhn/hato/ganadero/Voz;->elegida:Ljava/lang/String;

    .line 82
    :cond_3
    iget-object v1, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    const/high16 v3, 0x3f800000    # 1.0f

    invoke-virtual {v1, v3}, Landroid/speech/tts/TextToSpeech;->setSpeechRate(F)I

    .line 83
    iget-object v1, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    const/high16 v3, 0x3f800000    # 1.0f

    invoke-virtual {v1, v3}, Landroid/speech/tts/TextToSpeech;->setPitch(F)I
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 85
    const/16 v1, 0xf3c

    :try_start_2
    invoke-static {}, Landroid/speech/tts/TextToSpeech;->getMaxSpeechInputLength()I

    move-result v3

    invoke-static {v1, v3}, Ljava/lang/Math;->min(II)I
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_1
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    move-result v0

    :goto_1
    move v4, v2

    move v5, v2

    move-object v1, p1

    .line 88
    :goto_2
    :try_start_3
    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v2

    if-lez v2, :cond_0

    .line 90
    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v2

    if-gt v2, v0, :cond_4

    const-string v2, ""

    move-object v3, v2

    .line 97
    :goto_3
    new-instance v6, Landroid/os/Bundle;

    invoke-direct {v6}, Landroid/os/Bundle;-><init>()V

    .line 98
    iget-object v7, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v8, "rumi"

    invoke-virtual {v2, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v8

    add-int/lit8 v2, v4, 0x1

    invoke-virtual {v8, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v4

    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v7, v1, v5, v6, v4}, Landroid/speech/tts/TextToSpeech;->speak(Ljava/lang/CharSequence;ILandroid/os/Bundle;Ljava/lang/String;)I

    .line 99
    const/4 v5, 0x1

    move v4, v2

    move-object v1, v3

    .line 100
    goto :goto_2

    .line 92
    :cond_4
    const-string v2, ". "

    invoke-virtual {v1, v2, v0}, Ljava/lang/String;->lastIndexOf(Ljava/lang/String;I)I

    move-result v2

    .line 93
    div-int/lit8 v3, v0, 0x2

    if-ge v2, v3, :cond_5

    const/16 v2, 0x20

    invoke-virtual {v1, v2, v0}, Ljava/lang/String;->lastIndexOf(II)I

    move-result v2

    .line 94
    :cond_5
    if-gtz v2, :cond_6

    move v3, v0

    .line 95
    :goto_4
    const/4 v2, 0x0

    add-int/lit8 v6, v3, 0x1

    invoke-virtual {v1, v2, v6}, Ljava/lang/String;->substring(II)Ljava/lang/String;

    move-result-object v2

    add-int/lit8 v3, v3, 0x1

    invoke-virtual {v1, v3}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/String;->trim()Ljava/lang/String;
    :try_end_3
    .catch Ljava/lang/Exception; {:try_start_3 .. :try_end_3} :catch_0
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    move-result-object v3

    move-object v1, v2

    goto :goto_3

    .line 77
    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0

    .line 101
    :catch_0
    move-exception v0

    goto/16 :goto_0

    .line 85
    :catch_1
    move-exception v1

    goto :goto_1

    :cond_6
    move v3, v2

    goto :goto_4
.end method

.method public static hablando(Landroid/content/Context;)Z
    .locals 1

    .prologue
    .line 115
    :try_start_0
    invoke-static {p0}, Lhn/hato/ganadero/Voz;->de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;

    move-result-object v0

    iget-object v0, v0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v0}, Landroid/speech/tts/TextToSpeech;->isSpeaking()Z
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    move-result v0

    :goto_0
    return v0

    :catch_0
    move-exception v0

    const/4 v0, 0x0

    goto :goto_0
.end method

.method public static hablar(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V
    .locals 2

    .prologue
    .line 105
    if-nez p1, :cond_0

    .line 107
    :goto_0
    return-void

    .line 106
    :cond_0
    invoke-static {p0}, Lhn/hato/ganadero/Voz;->de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;

    move-result-object v0

    if-eqz p2, :cond_1

    invoke-virtual {p2}, Ljava/lang/String;->length()I

    move-result v1

    if-nez v1, :cond_2

    :cond_1
    const/4 p2, 0x0

    :cond_2
    invoke-direct {v0, p1, p2}, Lhn/hato/ganadero/Voz;->decir(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_0
.end method

.method private lengua()V
    .locals 3

    .prologue
    .line 39
    sget-object v0, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    iget-object v1, p0, Lhn/hato/ganadero/Voz;->lengua:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    .line 44
    :goto_0
    return-void

    .line 40
    :cond_0
    const-string v0, "es"

    sget-object v1, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1

    new-instance v0, Ljava/util/Locale;

    const-string v1, "es"

    const-string v2, "US"

    invoke-direct {v0, v1, v2}, Ljava/util/Locale;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    .line 41
    :goto_1
    :try_start_0
    iget-object v1, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v1, v0}, Landroid/speech/tts/TextToSpeech;->setLanguage(Ljava/util/Locale;)I
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 42
    :goto_2
    sget-object v0, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    iput-object v0, p0, Lhn/hato/ganadero/Voz;->lengua:Ljava/lang/String;

    .line 43
    const/4 v0, 0x0

    iput-object v0, p0, Lhn/hato/ganadero/Voz;->elegida:Ljava/lang/String;

    goto :goto_0

    .line 40
    :cond_1
    const-string v0, "pt"

    sget-object v1, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    new-instance v0, Ljava/util/Locale;

    const-string v1, "pt"

    const-string v2, "BR"

    invoke-direct {v0, v1, v2}, Ljava/util/Locale;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    goto :goto_1

    :cond_2
    const-string v0, "en"

    sget-object v1, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_3

    sget-object v0, Ljava/util/Locale;->US:Ljava/util/Locale;

    goto :goto_1

    :cond_3
    new-instance v0, Ljava/util/Locale;

    sget-object v1, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    invoke-direct {v0, v1}, Ljava/util/Locale;-><init>(Ljava/lang/String;)V

    goto :goto_1

    .line 41
    :catch_0
    move-exception v0

    goto :goto_2
.end method

.method public static lista(Landroid/content/Context;)Ljava/lang/String;
    .locals 8

    .prologue
    const/16 v7, 0x7d

    .line 120
    invoke-static {p0}, Lhn/hato/ganadero/Voz;->de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;

    move-result-object v2

    .line 121
    new-instance v0, Ljava/lang/StringBuilder;

    const-string v1, "{\"listo\":"

    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    iget-boolean v1, v2, Lhn/hato/ganadero/Voz;->listo:Z

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v1, ",\"voces\":["

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    .line 123
    :try_start_0
    iget-object v0, v2, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v0}, Landroid/speech/tts/TextToSpeech;->getVoices()Ljava/util/Set;

    move-result-object v0

    .line 124
    new-instance v1, Ljava/util/ArrayList;

    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

    .line 125
    if-eqz v0, :cond_2

    invoke-interface {v0}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v4

    :cond_0
    :goto_0
    invoke-interface {v4}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_2

    invoke-interface {v4}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/speech/tts/Voice;

    invoke-static {v0}, Lhn/hato/ganadero/Voz;->puntaje(Landroid/speech/tts/Voice;)I

    move-result v5

    if-ltz v5, :cond_0

    invoke-virtual {v1, v0}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    .line 137
    :catch_0
    move-exception v0

    .line 138
    :cond_1
    const-string v0, "],\"motor\":"

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 139
    :try_start_1
    iget-object v0, v2, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v0}, Landroid/speech/tts/TextToSpeech;->getDefaultEngine()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    .line 140
    :goto_1
    invoke-virtual {v3, v7}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0

    .line 126
    :cond_2
    :try_start_2
    new-instance v0, Lhn/hato/ganadero/Voz$1;

    invoke-direct {v0}, Lhn/hato/ganadero/Voz$1;-><init>()V

    invoke-static {v1, v0}, Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V

    .line 127
    const/4 v0, 0x1

    .line 128
    invoke-virtual {v1}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v4

    move v1, v0

    :goto_2
    invoke-interface {v4}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_1

    invoke-interface {v4}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/speech/tts/Voice;

    .line 129
    if-nez v1, :cond_3

    const/16 v1, 0x2c

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    .line 130
    :cond_3
    const/4 v1, 0x0

    .line 131
    const-string v5, "{\"n\":"

    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    invoke-virtual {v0}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v6

    invoke-static {v6}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    const-string v6, ",\"p\":"

    .line 132
    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    invoke-virtual {v0}, Landroid/speech/tts/Voice;->getLocale()Ljava/util/Locale;

    move-result-object v6

    invoke-virtual {v6}, Ljava/util/Locale;->getCountry()Ljava/lang/String;

    move-result-object v6

    invoke-static {v6}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v6

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    const-string v6, ",\"q\":"

    .line 133
    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    invoke-virtual {v0}, Landroid/speech/tts/Voice;->getQuality()I

    move-result v6

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v5

    const-string v6, ",\"red\":"

    .line 134
    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    invoke-virtual {v0}, Landroid/speech/tts/Voice;->isNetworkConnectionRequired()Z

    move-result v0

    invoke-virtual {v5, v0}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v0

    const/16 v5, 0x7d

    .line 135
    invoke-virtual {v0, v5}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_0

    goto :goto_2

    .line 139
    :catch_1
    move-exception v0

    const-string v0, "\"\""

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    goto :goto_1
.end method

.method private static puntaje(Landroid/speech/tts/Voice;)I
    .locals 4

    .prologue
    const/4 v0, -0x1

    .line 48
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getLocale()Ljava/util/Locale;

    move-result-object v1

    .line 49
    if-eqz v1, :cond_0

    sget-object v2, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    invoke-virtual {v1}, Ljava/util/Locale;->getLanguage()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_1

    .line 60
    :cond_0
    :goto_0
    return v0

    .line 50
    :cond_1
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getFeatures()Ljava/util/Set;

    move-result-object v2

    .line 51
    if-eqz v2, :cond_2

    const-string v3, "notInstalled"

    invoke-interface {v2, v3}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_0

    .line 52
    :cond_2
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getQuality()I

    move-result v0

    .line 53
    invoke-virtual {v1}, Ljava/util/Locale;->getCountry()Ljava/lang/String;

    move-result-object v1

    .line 54
    const-string v2, "US"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_3

    const-string v2, "MX"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_3

    const-string v2, "419"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_6

    :cond_3
    add-int/lit8 v0, v0, 0x3c

    .line 57
    :cond_4
    :goto_1
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->isNetworkConnectionRequired()Z

    move-result v1

    if-eqz v1, :cond_5

    add-int/lit8 v0, v0, -0x28

    .line 58
    :cond_5
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v1

    if-nez v1, :cond_8

    const-string v1, ""

    .line 59
    :goto_2
    const-string v2, "local"

    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v1

    if-eqz v1, :cond_0

    add-int/lit8 v0, v0, 0xa

    goto :goto_0

    .line 55
    :cond_6
    const-string v2, "ES"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_7

    add-int/lit8 v0, v0, -0x14

    goto :goto_1

    .line 56
    :cond_7
    if-eqz v1, :cond_4

    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v1

    if-lez v1, :cond_4

    add-int/lit8 v0, v0, 0x1e

    goto :goto_1

    .line 58
    :cond_8
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v1

    sget-object v2, Ljava/util/Locale;->ROOT:Ljava/util/Locale;

    invoke-virtual {v1, v2}, Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;

    move-result-object v1

    goto :goto_2
.end method

.method public static setIdioma(Ljava/lang/String;)V
    .locals 1

    .prologue
    .line 143
    if-eqz p0, :cond_0

    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result v0

    if-lez v0, :cond_0

    sput-object p0, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    :cond_0
    return-void
.end method


# virtual methods
.method public onInit(I)V
    .locals 3

    .prologue
    .line 28
    monitor-enter p0

    .line 29
    if-nez p1, :cond_1

    const/4 v0, 0x1

    :goto_0
    :try_start_0
    iput-boolean v0, p0, Lhn/hato/ganadero/Voz;->listo:Z

    .line 30
    iget-boolean v0, p0, Lhn/hato/ganadero/Voz;->listo:Z

    if-eqz v0, :cond_0

    .line 31
    invoke-direct {p0}, Lhn/hato/ganadero/Voz;->lengua()V

    .line 32
    iget-object v0, p0, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;

    if-eqz v0, :cond_0

    iget-object v0, p0, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;

    iget-object v1, p0, Lhn/hato/ganadero/Voz;->pendVoz:Ljava/lang/String;

    const/4 v2, 0x0

    iput-object v2, p0, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;

    invoke-direct {p0, v0, v1}, Lhn/hato/ganadero/Voz;->decir(Ljava/lang/String;Ljava/lang/String;)V

    .line 34
    :cond_0
    monitor-exit p0

    .line 35
    return-void

    .line 29
    :cond_1
    const/4 v0, 0x0

    goto :goto_0

    .line 34
    :catchall_0
    move-exception v0

    monitor-exit p0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v0
.end method
