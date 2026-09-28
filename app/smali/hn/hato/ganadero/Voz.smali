.class public final Lhn/hato/ganadero/Voz;
.super Ljava/lang/Object;
.source "Voz.java"

# interfaces
.implements Landroid/speech/tts/TextToSpeech$OnInitListener;


# static fields
.field private static uno:Lhn/hato/ganadero/Voz;

.field public static idioma:Ljava/lang/String; = "es"


# instance fields
.field private final ctx:Landroid/content/Context;

.field private elegida:Ljava/lang/String;

.field private listo:Z

.field private pendVoz:Ljava/lang/String;

.field private pendiente:Ljava/lang/String;

.field private tts:Landroid/speech/tts/TextToSpeech;


# direct methods
.method private constructor <init>(Landroid/content/Context;)V
    .locals 1

    .line 20
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    invoke-virtual {p1}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p1

    iput-object p1, p0, Lhn/hato/ganadero/Voz;->ctx:Landroid/content/Context;

    new-instance p1, Landroid/speech/tts/TextToSpeech;

    iget-object v0, p0, Lhn/hato/ganadero/Voz;->ctx:Landroid/content/Context;

    invoke-direct {p1, v0, p0}, Landroid/speech/tts/TextToSpeech;-><init>(Landroid/content/Context;Landroid/speech/tts/TextToSpeech$OnInitListener;)V

    iput-object p1, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    return-void
.end method

.method public static ajustes(Landroid/content/Context;)V
    .locals 3

    .line 133
    const/high16 v0, 0x10000000

    :try_start_0
    new-instance v1, Landroid/content/Intent;

    const-string v2, "com.android.settings.TTS_SETTINGS"

    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 134
    invoke-virtual {v1, v0}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 135
    invoke-virtual {p0, v1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 142
    goto :goto_1

    .line 136
    :catch_0
    move-exception v1

    .line 138
    :try_start_1
    new-instance v1, Landroid/content/Intent;

    const-string v2, "android.speech.tts.engine.INSTALL_TTS_DATA"

    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 139
    invoke-virtual {v1, v0}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 140
    invoke-virtual {p0, v1}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_0

    .line 141
    :catch_1
    move-exception p0

    :goto_0
    nop

    .line 143
    :goto_1
    return-void
.end method

.method private buscar(Ljava/lang/String;)Landroid/speech/tts/Voice;
    .locals 5

    .line 53
    const/4 v0, 0x0

    :try_start_0
    iget-object v1, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v1}, Landroid/speech/tts/TextToSpeech;->getVoices()Ljava/util/Set;

    move-result-object v1
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 54
    if-nez v1, :cond_0

    return-object v0

    .line 55
    :cond_0
    nop

    .line 56
    invoke-interface {v1}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v1

    const/4 v2, -0x1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_3

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Landroid/speech/tts/Voice;

    .line 57
    if-eqz p1, :cond_1

    invoke-virtual {v3}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {p1, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_1

    return-object v3

    .line 58
    :cond_1
    invoke-static {v3}, Lhn/hato/ganadero/Voz;->puntaje(Landroid/speech/tts/Voice;)I

    move-result v4

    .line 59
    if-le v4, v2, :cond_2

    move-object v0, v3

    move v2, v4

    .line 60
    :cond_2
    goto :goto_0

    .line 61
    :cond_3
    return-object v0

    .line 53
    :catch_0
    move-exception p1

    return-object v0
.end method

.method public static callar(Landroid/content/Context;)V
    .locals 1

    .line 97
    invoke-static {p0}, Lhn/hato/ganadero/Voz;->de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;

    move-result-object p0

    .line 98
    monitor-enter p0

    const/4 v0, 0x0

    :try_start_0
    iput-object v0, p0, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    :try_start_1
    iget-object v0, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v0}, Landroid/speech/tts/TextToSpeech;->stop()I
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_0

    :catch_0
    move-exception v0

    :goto_0
    :try_start_2
    monitor-exit p0

    .line 99
    return-void

    .line 98
    :catchall_0
    move-exception v0

    monitor-exit p0
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    throw v0
.end method

.method private static declared-synchronized de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;
    .locals 2

    const-class v0, Lhn/hato/ganadero/Voz;

    monitor-enter v0

    .line 22
    :try_start_0
    sget-object v1, Lhn/hato/ganadero/Voz;->uno:Lhn/hato/ganadero/Voz;

    if-nez v1, :cond_0

    new-instance v1, Lhn/hato/ganadero/Voz;

    invoke-direct {v1, p0}, Lhn/hato/ganadero/Voz;-><init>(Landroid/content/Context;)V

    sput-object v1, Lhn/hato/ganadero/Voz;->uno:Lhn/hato/ganadero/Voz;

    :cond_0
    sget-object p0, Lhn/hato/ganadero/Voz;->uno:Lhn/hato/ganadero/Voz;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit v0

    return-object p0

    .line 22
    :catchall_0
    move-exception p0

    :try_start_1
    monitor-exit v0
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    throw p0
.end method

.method private declared-synchronized decir(Ljava/lang/String;Ljava/lang/String;)V
    .locals 9

    monitor-enter p0

    .line 65
    :try_start_0
    iget-boolean v0, p0, Lhn/hato/ganadero/Voz;->listo:Z

    if-nez v0, :cond_0

    iput-object p1, p0, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;

    iput-object p2, p0, Lhn/hato/ganadero/Voz;->pendVoz:Ljava/lang/String;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit p0

    return-void

    .line 67
    :cond_0
    :try_start_1
    invoke-direct {p0, p2}, Lhn/hato/ganadero/Voz;->buscar(Ljava/lang/String;)Landroid/speech/tts/Voice;

    move-result-object p2

    .line 68
    if-eqz p2, :cond_2

    iget-object v0, p0, Lhn/hato/ganadero/Voz;->elegida:Ljava/lang/String;

    if-eqz v0, :cond_1

    iget-object v0, p0, Lhn/hato/ganadero/Voz;->elegida:Ljava/lang/String;

    invoke-virtual {p2}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_2

    :cond_1
    iget-object v0, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v0, p2}, Landroid/speech/tts/TextToSpeech;->setVoice(Landroid/speech/tts/Voice;)I

    invoke-virtual {p2}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object p2

    iput-object p2, p0, Lhn/hato/ganadero/Voz;->elegida:Ljava/lang/String;

    .line 69
    :cond_2
    iget-object p2, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    const/high16 v0, 0x3f800000    # 1.0f

    invoke-virtual {p2, v0}, Landroid/speech/tts/TextToSpeech;->setSpeechRate(F)I

    .line 70
    iget-object p2, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {p2, v0}, Landroid/speech/tts/TextToSpeech;->setPitch(F)I
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 71
    nop

    .line 72
    const/16 p2, 0xf3c

    :try_start_2
    invoke-static {}, Landroid/speech/tts/TextToSpeech;->getMaxSpeechInputLength()I

    move-result v0

    invoke-static {p2, v0}, Ljava/lang/Math;->min(II)I

    move-result p2
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_0
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    goto :goto_0

    :catch_0
    move-exception v0

    .line 73
    :goto_0
    nop

    .line 74
    const/4 v0, 0x0

    const/4 v1, 0x0

    const/4 v2, 0x0

    .line 75
    :goto_1
    :try_start_3
    invoke-virtual {p1}, Ljava/lang/String;->length()I

    move-result v3

    if-lez v3, :cond_6

    .line 77
    invoke-virtual {p1}, Ljava/lang/String;->length()I

    move-result v3

    const/4 v4, 0x1

    if-gt v3, p2, :cond_3

    const-string v3, ""

    goto :goto_2

    .line 79
    :cond_3
    const-string v3, ". "

    invoke-virtual {p1, v3, p2}, Ljava/lang/String;->lastIndexOf(Ljava/lang/String;I)I

    move-result v3

    .line 80
    div-int/lit8 v5, p2, 0x2

    if-ge v3, v5, :cond_4

    const/16 v3, 0x20

    invoke-virtual {p1, v3, p2}, Ljava/lang/String;->lastIndexOf(II)I

    move-result v3

    .line 81
    :cond_4
    if-gtz v3, :cond_5

    move v3, p2

    .line 82
    :cond_5
    add-int/2addr v3, v4

    invoke-virtual {p1, v0, v3}, Ljava/lang/String;->substring(II)Ljava/lang/String;

    move-result-object v5

    invoke-virtual {p1, v3}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object p1

    move-object v3, p1

    move-object p1, v5

    .line 84
    :goto_2
    new-instance v5, Landroid/os/Bundle;

    invoke-direct {v5}, Landroid/os/Bundle;-><init>()V

    .line 85
    iget-object v6, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    new-instance v7, Ljava/lang/StringBuilder;

    invoke-direct {v7}, Ljava/lang/StringBuilder;-><init>()V

    const-string v8, "rumi"

    invoke-virtual {v7, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v7

    add-int/lit8 v8, v2, 0x1

    invoke-virtual {v7, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v6, p1, v1, v5, v2}, Landroid/speech/tts/TextToSpeech;->speak(Ljava/lang/CharSequence;ILandroid/os/Bundle;Ljava/lang/String;)I
    :try_end_3
    .catch Ljava/lang/Exception; {:try_start_3 .. :try_end_3} :catch_1
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    .line 86
    nop

    .line 87
    move-object p1, v3

    move v2, v8

    const/4 v1, 0x1

    goto :goto_1

    .line 88
    :catch_1
    move-exception p1

    :cond_6
    nop

    .line 89
    monitor-exit p0

    return-void

    .line 64
    :catchall_0
    move-exception p1

    :try_start_4
    monitor-exit p0
    :try_end_4
    .catchall {:try_start_4 .. :try_end_4} :catchall_0

    throw p1
.end method

.method public static hablando(Landroid/content/Context;)Z
    .locals 0

    .line 102
    :try_start_0
    invoke-static {p0}, Lhn/hato/ganadero/Voz;->de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;

    move-result-object p0

    iget-object p0, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {p0}, Landroid/speech/tts/TextToSpeech;->isSpeaking()Z

    move-result p0
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return p0

    :catch_0
    move-exception p0

    const/4 p0, 0x0

    return p0
.end method

.method public static hablar(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V
    .locals 1

    .line 92
    if-nez p1, :cond_0

    return-void

    .line 93
    :cond_0
    invoke-static {p0}, Lhn/hato/ganadero/Voz;->de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;

    move-result-object p0

    if-eqz p2, :cond_1

    invoke-virtual {p2}, Ljava/lang/String;->length()I

    move-result v0

    if-nez v0, :cond_2

    :cond_1
    const/4 p2, 0x0

    :cond_2
    invoke-direct {p0, p1, p2}, Lhn/hato/ganadero/Voz;->decir(Ljava/lang/String;Ljava/lang/String;)V

    .line 94
    return-void
.end method

.method static synthetic lambda$lista$0(Landroid/speech/tts/Voice;Landroid/speech/tts/Voice;)I
    .locals 0

    .line 113
    invoke-static {p1}, Lhn/hato/ganadero/Voz;->puntaje(Landroid/speech/tts/Voice;)I

    move-result p1

    invoke-static {p0}, Lhn/hato/ganadero/Voz;->puntaje(Landroid/speech/tts/Voice;)I

    move-result p0

    sub-int/2addr p1, p0

    return p1
.end method

.method public static lista(Landroid/content/Context;)Ljava/lang/String;
    .locals 6

    .line 107
    invoke-static {p0}, Lhn/hato/ganadero/Voz;->de(Landroid/content/Context;)Lhn/hato/ganadero/Voz;

    move-result-object p0

    .line 108
    new-instance v0, Ljava/lang/StringBuilder;

    const-string v1, "{\"listo\":"

    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    iget-boolean v1, p0, Lhn/hato/ganadero/Voz;->listo:Z

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v0

    const-string v1, ",\"voces\":["

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v0

    .line 110
    const/16 v1, 0x7d

    :try_start_0
    iget-object v2, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {v2}, Landroid/speech/tts/TextToSpeech;->getVoices()Ljava/util/Set;

    move-result-object v2

    .line 111
    new-instance v3, Ljava/util/ArrayList;

    invoke-direct {v3}, Ljava/util/ArrayList;-><init>()V

    .line 112
    if-eqz v2, :cond_1

    invoke-interface {v2}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v2

    :cond_0
    :goto_0
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z

    move-result v4

    if-eqz v4, :cond_1

    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Landroid/speech/tts/Voice;

    invoke-static {v4}, Lhn/hato/ganadero/Voz;->puntaje(Landroid/speech/tts/Voice;)I

    move-result v5

    if-ltz v5, :cond_0

    invoke-virtual {v3, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    goto :goto_0

    .line 113
    :cond_1
    new-instance v2, Lhn/hato/ganadero/Voz$$ExternalSyntheticLambda0;

    invoke-direct {v2}, Lhn/hato/ganadero/Voz$$ExternalSyntheticLambda0;-><init>()V

    invoke-static {v3, v2}, Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V

    .line 114
    nop

    .line 115
    invoke-virtual {v3}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v2

    const/4 v3, 0x1

    :goto_1
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z

    move-result v4

    if-eqz v4, :cond_3

    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Landroid/speech/tts/Voice;

    .line 116
    if-nez v3, :cond_2

    const/16 v3, 0x2c

    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    .line 117
    :cond_2
    nop

    .line 118
    const-string v3, "{\"n\":"

    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v4}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v5

    invoke-static {v5}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v5, ",\"p\":"

    .line 119
    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v4}, Landroid/speech/tts/Voice;->getLocale()Ljava/util/Locale;

    move-result-object v5

    invoke-virtual {v5}, Ljava/util/Locale;->getCountry()Ljava/lang/String;

    move-result-object v5

    invoke-static {v5}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v5, ",\"q\":"

    .line 120
    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v4}, Landroid/speech/tts/Voice;->getQuality()I

    move-result v5

    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v5, ",\"red\":"

    .line 121
    invoke-virtual {v3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v4}, Landroid/speech/tts/Voice;->isNetworkConnectionRequired()Z

    move-result v4

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v3

    .line 122
    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 123
    const/4 v3, 0x0

    goto :goto_1

    .line 124
    :catch_0
    move-exception v2

    :cond_3
    nop

    .line 125
    const-string v2, "],\"motor\":"

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 126
    :try_start_1
    iget-object p0, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    invoke-virtual {p0}, Landroid/speech/tts/TextToSpeech;->getDefaultEngine()Ljava/lang/String;

    move-result-object p0

    invoke-static {p0}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object p0

    invoke-static {p0}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p0

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_2

    :catch_1
    move-exception p0

    const-string p0, "\"\""

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 127
    :goto_2
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method

.method private static puntaje(Landroid/speech/tts/Voice;)I
    .locals 4

    .line 36
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getLocale()Ljava/util/Locale;

    move-result-object v0

    .line 37
    const/4 v1, -0x1

    if-eqz v0, :cond_9

    sget-object v2, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    invoke-virtual {v0}, Ljava/util/Locale;->getLanguage()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_0

    goto/16 :goto_3

    .line 38
    :cond_0
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getFeatures()Ljava/util/Set;

    move-result-object v2

    .line 39
    if-eqz v2, :cond_1

    const-string v3, "notInstalled"

    invoke-interface {v2, v3}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_1

    return v1

    .line 40
    :cond_1
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getQuality()I

    move-result v1

    .line 41
    invoke-virtual {v0}, Ljava/util/Locale;->getCountry()Ljava/lang/String;

    move-result-object v0

    .line 42
    const-string v2, "US"

    invoke-virtual {v2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_4

    const-string v2, "MX"

    invoke-virtual {v2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_4

    const-string v2, "419"

    invoke-virtual {v2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_2

    goto :goto_0

    .line 43
    :cond_2
    const-string v2, "ES"

    invoke-virtual {v2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_3

    add-int/lit8 v1, v1, -0x14

    goto :goto_1

    .line 44
    :cond_3
    if-eqz v0, :cond_5

    invoke-virtual {v0}, Ljava/lang/String;->length()I

    move-result v0

    if-lez v0, :cond_5

    add-int/lit8 v1, v1, 0x1e

    goto :goto_1

    .line 42
    :cond_4
    :goto_0
    add-int/lit8 v1, v1, 0x3c

    .line 45
    :cond_5
    :goto_1
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->isNetworkConnectionRequired()Z

    move-result v0

    if-eqz v0, :cond_6

    add-int/lit8 v1, v1, -0x28

    .line 46
    :cond_6
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object v0

    if-nez v0, :cond_7

    const-string p0, ""

    goto :goto_2

    :cond_7
    invoke-virtual {p0}, Landroid/speech/tts/Voice;->getName()Ljava/lang/String;

    move-result-object p0

    sget-object v0, Ljava/util/Locale;->ROOT:Ljava/util/Locale;

    invoke-virtual {p0, v0}, Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;

    move-result-object p0

    .line 47
    :goto_2
    const-string v0, "local"

    invoke-virtual {p0, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result p0

    if-eqz p0, :cond_8

    add-int/lit8 v1, v1, 0xa

    .line 48
    :cond_8
    return v1

    .line 37
    :cond_9
    :goto_3
    return v1
.end method


# virtual methods
.method public onInit(I)V
    .locals 3

    .line 25
    monitor-enter p0

    .line 26
    if-nez p1, :cond_0

    const/4 p1, 0x1

    goto :goto_0

    :cond_0
    const/4 p1, 0x0

    :goto_0
    :try_start_0
    iput-boolean p1, p0, Lhn/hato/ganadero/Voz;->listo:Z

    .line 27
    iget-boolean p1, p0, Lhn/hato/ganadero/Voz;->listo:Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    if-eqz p1, :cond_1

    .line 28
    :try_start_1
    iget-object p1, p0, Lhn/hato/ganadero/Voz;->tts:Landroid/speech/tts/TextToSpeech;

    new-instance v0, Ljava/util/Locale;

    const-string v1, "es"

    const-string v2, "US"

    invoke-direct {v0, v1, v2}, Ljava/util/Locale;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {p1, v0}, Landroid/speech/tts/TextToSpeech;->setLanguage(Ljava/util/Locale;)I
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_1

    :catch_0
    move-exception p1

    .line 29
    :goto_1
    :try_start_2
    iget-object p1, p0, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;

    if-eqz p1, :cond_1

    iget-object p1, p0, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;

    iget-object v0, p0, Lhn/hato/ganadero/Voz;->pendVoz:Ljava/lang/String;

    const/4 v1, 0x0

    iput-object v1, p0, Lhn/hato/ganadero/Voz;->pendiente:Ljava/lang/String;

    invoke-direct {p0, p1, v0}, Lhn/hato/ganadero/Voz;->decir(Ljava/lang/String;Ljava/lang/String;)V

    .line 31
    :cond_1
    monitor-exit p0

    .line 32
    return-void

    .line 31
    :catchall_0
    move-exception p1

    monitor-exit p0
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    throw p1
.end method

.method public static setIdioma(Ljava/lang/String;)V
    .locals 1

    if-eqz p0, :cond_0

    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result v0

    if-eqz v0, :cond_0

    sput-object p0, Lhn/hato/ganadero/Voz;->idioma:Ljava/lang/String;

    :cond_0
    return-void
.end method
