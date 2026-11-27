// Nodo de la torre de agua: ESP32 + HC-SR04 + LCD 16x2 I2C (+ 3 LED y zumbador)
// Mide 7 veces, toma la mediana, muestra nivel y estado en la pantalla y envía la
// distancia a la Edge Function recibir-lectura de Supabase. Si no hay Wi-Fi guarda
// hasta 48 mediciones y las reenvía con su antigüedad (edad_min).
// Requiere la biblioteca "LiquidCrystal I2C" (Frank de Brabander) y
// los datos de Wi-Fi y token de abajo (para la entrega, moverlos a config_local.h).

#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>

// ---- Configuración (completa solo tu Wi-Fi: 2,4 GHz) ----
#define WIFI_SSID      "TU_WIFI"
#define WIFI_PASS      "TU_CLAVE_WIFI"
#define SUPABASE_URL   "https://tzjziqjrxgquzkjbldml.supabase.co"
#define TOKEN_NODO     "PEGAR_TOKEN_DEL_NODO"

const char* DEV_EUI = "nodo-torre-01";   // debe existir en la tabla nodo

// Certificado raíz/emisor (WE1 - Google Trust Services) para validar HTTPS de Supabase
// Válido hasta: 20/02/2029
const char* SUPABASE_CA_CERT = R"EOF(
-----BEGIN CERTIFICATE-----
MIICnzCCAiWgAwIBAgIQf/MZd5csIkp2FV0TttaF4zAKBggqhkjOPQQDAzBHMQsw
CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEU
MBIGA1UEAxMLR1RTIFJvb3QgUjQwHhcNMjMxMjEzMDkwMDAwWhcNMjkwMjIwMTQw
MDAwWjA7MQswCQYDVQQGEwJVUzEeMBwGA1UEChMVR29vZ2xlIFRydXN0IFNlcnZp
Y2VzMQwwCgYDVQQDEwNXRTEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARvzTr+
Z1dHTCEDhUDCR127WEcPQMFcF4XGGTfn1XzthkubgdnXGhOlCgP4mMTG6J7/EFmP
LCaY9eYmJbsPAvpWo4H+MIH7MA4GA1UdDwEB/wQEAwIBhjAdBgNVHSUEFjAUBggr
BgEFBQcDAQYIKwYBBQUHAwIwEgYDVR0TAQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQU
kHeSNWfE/6jMqeZ72YB5e8yT+TgwHwYDVR0jBBgwFoAUgEzW63T/STaj1dj8tT7F
avCUHYwwNAYIKwYBBQUHAQEEKDAmMCQGCCsGAQUFBzAChhhodHRwOi8vaS5wa2ku
Z29vZy9yNC5jcnQwKwYDVR0fBCQwIjAgoB6gHIYaaHR0cDovL2MucGtpLmdvb2cv
ci9yNC5jcmwwEwYDVR0gBAwwCjAIBgZngQwBAgEwCgYIKoZIzj0EAwMDaAAwZQIx
AOcCq1HW90OVznX+0RGU1cxAQXomvtgM8zItPZCuFQ8jSBJSjz5keROv9aYsAm5V
sQIwJonMaAFi54mrfhfoFNZEfuNMSQ6/bIBiNLiyoX46FohQvKeIoJ99cx7sUkFN
7uJW
-----END CERTIFICATE-----
)EOF";

// Pines (Anexo C del informe)
const int TRIG_PIN = 5;
const int ECHO_PIN = 18;
const int LED_VERDE = 25;
const int LED_AMARILLO = 26;
const int LED_ROJO = 27;
const int ZUMBADOR = 14;                 // zumbador activo

// Calibración de la torre: distancia del sensor al agua.
// Debe coincidir con la tabla calibracion de Supabase (en mm).
const float DIST_LLENO_CM = 3.0;
const float DIST_VACIO_CM = 16.1;     

// Para probar usa 15000 (15 s). Para el uso real, 900000 (15 minutos).
const unsigned long INTERVALO_ENVIO_MS = 5000;

const int MAX_PENDIENTES = 48;
struct Pendiente { int mm; uint16_t seq; unsigned long t; };
Pendiente cola[MAX_PENDIENTES];
int nCola = 0;
uint16_t seq = 0;                        // vuelve a 0 después de 65535

unsigned long ultimoEnvio = 0;
unsigned long ultimoIntentoWifi = 0;
bool primeraVez = true;
String marca = "...";                    // estado del envío en la pantalla: OK, ERR u OFF

LiquidCrystal_I2C lcd(0x27, 16, 2);      // si no se ve nada, probar 0x3F

// Escribe una línea de 16 caracteres, rellenando con espacios para borrar lo anterior
void linea(int fila, String texto) {
  while (texto.length() < 16) texto += " ";
  lcd.setCursor(0, fila);
  lcd.print(texto.substring(0, 16));
}

void mostrarMarca() {
  String m = marca;
  while (m.length() < 3) m += " ";
  lcd.setCursor(13, 0);
  lcd.print(m.substring(0, 3));
}

void leds(bool verde, bool amarillo, bool rojo) {
  digitalWrite(LED_VERDE, verde);
  digitalWrite(LED_AMARILLO, amarillo);
  digitalWrite(LED_ROJO, rojo);
}

// Una medición en cm (-1 si no hay eco o está fuera de rango)
float medirCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(5);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long dur = pulseIn(ECHO_PIN, HIGH, 25000);
  if (dur == 0) return -1;

  float cm = dur * 0.0343 / 2.0;
  if (cm < 2.5 || cm > 300) return -1;
  return cm;
}

// Mediana de 7 mediciones (descarta ecos falsos aislados); -1 si hay menos de 3 válidas
float medirMediana() {
  float v[7];
  int n = 0;
  for (int i = 0; i < 7; i++) {
    float cm = medirCm();
    if (cm > 0) v[n++] = cm;
    delay(60);
  }
  if (n < 3) return -1;
  for (int i = 0; i < n - 1; i++) {
    for (int j = i + 1; j < n; j++) {
      if (v[j] < v[i]) { float t = v[i]; v[i] = v[j]; v[j] = t; }
    }
  }
  return v[n / 2];
}

void conectarWiFi() {
  lcd.clear();
  linea(0, "Conectando WiFi");
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  for (int i = 0; i < 40 && WiFi.status() != WL_CONNECTED; i++) delay(500);
  linea(0, WiFi.status() == WL_CONNECTED ? "WiFi OK" : "WiFi fallo");
  Serial.println(WiFi.status() == WL_CONNECTED ? "WiFi conectado" : "WiFi sin conexion (se reintenta)");
  delay(1000);
  ultimoIntentoWifi = millis();
}

// Guarda una medición en la cola (si está llena, descarta la más antigua)
void encolar(int mm) {
  if (nCola == MAX_PENDIENTES) {
    for (int i = 1; i < nCola; i++) cola[i - 1] = cola[i];
    nCola--;
  }
  cola[nCola++] = { mm, seq++, millis() };
}

void quitarPrimero() {
  for (int i = 1; i < nCola; i++) cola[i - 1] = cola[i];
  nCola--;
}

// Devuelve el código HTTP (201 = guardado, 200 = repetida); negativo si no hubo conexión
int enviar(const Pendiente& p) {
  if (WiFi.status() != WL_CONNECTED) return -1;

  WiFiClientSecure client;
  client.setCACert(SUPABASE_CA_CERT);

  HTTPClient http;
  http.setTimeout(8000);
  if (!http.begin(client, String(SUPABASE_URL) + "/functions/v1/recibir-lectura")) return -2;
  http.addHeader("Content-Type", "application/json");
  http.addHeader("x-token", TOKEN_NODO);

  unsigned long edadMin = (millis() - p.t) / 60000UL;
  String cuerpo = String("{\"dev_eui\":\"") + DEV_EUI + "\",\"seq\":" + p.seq +
                  ",\"distancia_mm\":" + p.mm + ",\"edad_min\":" + edadMin + "}";
  int codigo = http.POST(cuerpo);
  Serial.printf("POST %s -> %d\n", cuerpo.c_str(), codigo);
  if (codigo > 0) Serial.println(http.getString());
  http.end();
  return codigo;
}

// Envía las mediciones pendientes, de la más antigua a la más nueva
void vaciarCola() {
  while (nCola > 0) {
    int codigo = enviar(cola[0]);
    if (codigo == 200 || codigo == 201) {
      marca = "OK";
      quitarPrimero();
    } else if (codigo >= 400 && codigo < 500) {
      marca = "ERR";          // la función la rechazó (token, nodo o rango): no se reintenta
      quitarPrimero();
    } else {
      marca = codigo == -1 ? "OFF" : "ERR";
      break;                  // sin conexión o falla del servidor: se reintenta después
    }
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(LED_VERDE, OUTPUT);
  pinMode(LED_AMARILLO, OUTPUT);
  pinMode(LED_ROJO, OUTPUT);
  pinMode(ZUMBADOR, OUTPUT);
  Wire.begin(21, 22);          // SDA = 21, SCL = 22
  lcd.init();
  lcd.backlight();
  lcd.clear();
  linea(0, "Iniciando...");
  Serial.println("Iniciando medicion...");
  conectarWiFi();
}

void loop() {
  float d = medirMediana();

  if (d < 0) {
    Serial.println("Sin lectura valida (fuera de rango o sin eco)");
    linea(0, "Sin lectura");
    linea(1, "Revisar sensor");
    leds(false, false, false);
  } else {
    float pct = (DIST_VACIO_CM - d) / (DIST_VACIO_CM - DIST_LLENO_CM) * 100.0;
    pct = constrain(pct, 0, 100);

    const char* estado;
    const char* textoLcd;
    if (pct >= 95)      { estado = "LLENA";    textoLcd = "LLENA: detener";   leds(true, false, false); }
    else if (pct < 15)  { estado = "CRITICO";  textoLcd = "CRITICO: bombear"; leds(false, false, true); }
    else if (pct < 30)  { estado = "ATENCION"; textoLcd = "ATENCION";         leds(false, true, false); }
    else                { estado = "NORMAL";   textoLcd = "Normal";           leds(true, false, false); }

    Serial.print("Distancia: ");
    Serial.print(d, 1);
    Serial.print(" cm | Nivel: ");
    Serial.print(pct, 0);
    Serial.print(" % | Estado: ");
    Serial.println(estado);

    linea(0, "Torre: " + String((int)round(pct)) + " %");
    linea(1, textoLcd);

    if (pct < 15) {            // aviso sonoro solo en nivel crítico
      digitalWrite(ZUMBADOR, HIGH);
      delay(120);
      digitalWrite(ZUMBADOR, LOW);
    }

    if (primeraVez || millis() - ultimoEnvio >= INTERVALO_ENVIO_MS) {
      primeraVez = false;
      ultimoEnvio = millis();
      encolar((int)round(d * 10));        // cm a mm
    }
  }

  // Wi-Fi: reintenta cada 10 s si se cayó; con conexión, envía lo pendiente
  if (WiFi.status() != WL_CONNECTED) {
    marca = "OFF";
    if (millis() - ultimoIntentoWifi > 10000) {
      WiFi.disconnect();
      WiFi.begin(WIFI_SSID, WIFI_PASS);
      ultimoIntentoWifi = millis();
    }
  } else if (nCola > 0) {
    vaciarCola();
  }
  mostrarMarca();

  delay(700);
}