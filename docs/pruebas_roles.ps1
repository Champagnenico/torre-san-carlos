# Pruebas de permisos por rol con cuentas reales (Operador, Mantencion, Reemplazante).
# Las claves se escriben en tu PC, no se envian a nadie mas que a Supabase (inicio de sesion normal).
# No modifica datos: el PATCH reescribe el umbral con su mismo valor (15) y solo se espera 0 filas afectadas.
$U = "https://tzjziqjrxgquzkjbldml.supabase.co"
$K = "sb_publishable_JRIYQnzJUw_NCfu7hDuhLA_lQ5rH5Yq"

function Llamar($metodo, $ruta, $token, $body) {
  $h = @{ apikey = $K; Authorization = "Bearer $token"; Prefer = "return=representation" }
  $p = @{ Uri = "$U$ruta"; Method = $metodo; Headers = $h; UseBasicParsing = $true; ErrorAction = "Stop" }
  if ($body) { $p.Body = $body; $p.ContentType = "application/json" }
  try { $r = Invoke-WebRequest @p; return @{ code = [int]$r.StatusCode; txt = $r.Content } }
  catch {
    $c = [int]$_.Exception.Response.StatusCode
    $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    return @{ code = $c; txt = $sr.ReadToEnd() }
  }
}
function Filas($txt) {
  $t = "$txt".Trim()
  if ($t -eq "" -or $t -eq "[]") { return 0 }
  try { $o = ConvertFrom-Json $t; if ($o -is [array]) { return $o.Count } else { return 1 } } catch { return -1 }
}

$roles = @("Operador", "Mantencion", "Reemplazante")
$salida = @()
foreach ($rol in $roles) {
  Write-Host ""
  Write-Host "=== Cuenta de $rol ==="
  $mail = Read-Host "Correo de la cuenta $rol"
  $sec = Read-Host "Contrasena de la cuenta $rol" -AsSecureString
  $pw = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
  $login = Invoke-RestMethod -Method Post -Uri "$U/auth/v1/token?grant_type=password" -Headers @{ apikey = $K } `
           -ContentType "application/json" -Body (@{ email = $mail; password = $pw } | ConvertTo-Json)
  $t = $login.access_token
  if (-not $t) { $salida += "$rol | no se pudo iniciar sesion"; continue }

  $r = Llamar "GET" "/rest/v1/v_nivel_actual?select=*&limit=1" $t $null
  $salida += "$rol | Ver nivel actual (v_nivel_actual) | HTTP $($r.code) | filas $(Filas $r.txt)"
  $r = Llamar "GET" "/rest/v1/lectura?select=id_lectura&limit=1" $t $null
  $salida += "$rol | Ver lecturas | HTTP $($r.code) | filas $(Filas $r.txt)"
  $r = Llamar "GET" "/rest/v1/nodo?select=id_nodo&limit=1" $t $null
  $salida += "$rol | Ver detalle de nodos | HTTP $($r.code) | filas $(Filas $r.txt)"
  $r = Llamar "PATCH" "/rest/v1/umbral?tipo=eq.CRITICA" $t '{"valor_pct":15}'
  $salida += "$rol | Editar umbral (mismo valor 15) | HTTP $($r.code) | filas afectadas $(Filas $r.txt)"
  $r = Llamar "POST" "/rest/v1/lectura" $t '{"id_nodo":1,"ts_lectura":"2026-01-01T00:00:00Z","seq":1,"distancia_mm":1000,"nivel_pct":50,"volumen_l":15000,"validez":"VALIDO"}'
  $salida += "$rol | Insertar una medicion | HTTP $($r.code) | $($r.txt)"
  $r = Llamar "POST" "/functions/v1/crear-usuario" $t '{}'
  $salida += "$rol | Llamar crear-usuario | HTTP $($r.code) | $($r.txt)"
}
Write-Host ""
$salida | ForEach-Object { Write-Host $_ }
$salida | Out-File -Encoding utf8 "$PSScriptRoot\resultados_roles.txt"
Write-Host ""
Write-Host "Listo. Resultados en resultados_roles.txt. Copialos y pegalos en el chat (no incluyen claves)."
