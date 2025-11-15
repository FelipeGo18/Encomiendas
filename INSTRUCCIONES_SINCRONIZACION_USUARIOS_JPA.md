# 📋 INSTRUCCIONES: Sincronización de Usuarios desde API GlassFish (JPA)

## 🎯 Objetivo
Cambiar el sistema para que los usuarios se carguen desde el servidor GlassFish (con JPA) en lugar de crearlos localmente en la app Android.

---

## 🔧 PARTE 1: Modificar el Servidor GlassFish

### 1.1. Ubicar el archivo UserService.java
En tu proyecto de GlassFish, busca el archivo:
```
src/main/java/com/encomiendas/service/UserService.java
```

### 1.2. Reemplazar el contenido del UserService.java
Copia el contenido del archivo que generé:
```
SERVIDOR_GLASSFISH_UserService.java
```

Este archivo ya está en la raíz de tu proyecto Android para que lo copies al servidor.

### 1.3. Cambios principales en UserService.java:
✅ **Solo carga 5 usuarios específicos:**
1. `remitente.demo@gmail.com` - REMITENTE - password: `123456`
2. `operador@gmail.com` - OPERADOR_HUB - password: `123456`
3. `repartidor1@gmail.com` - REPARTIDOR - password: `123456`
4. `asignador@gmail.com` - ASIGNADOR - password: `123456`
5. `admin@gmail.com` - ADMIN - password: `123456`

✅ **Las contraseñas se hashean automáticamente con SHA-256**

✅ **Se inicializan al arrancar el servidor**

### 1.4. Desplegar en GlassFish
1. Reconstruye tu proyecto Java EE
2. Despliega la aplicación en GlassFish
3. Verifica que el servidor esté corriendo en: `http://localhost:8080/EncomiendasAPI`

---

## 📱 PARTE 2: Modificaciones en la App Android (YA REALIZADAS)

### 2.1. DemoSeeder.java - MODIFICADO ✅
**Cambios realizados:**

#### Antes (creaba usuarios localmente):
```java
// Creaba usuarios directamente en Room
long remitenteId = ensureUser(udao, "remitente.demo@gmail.com", "123456", "REMITENTE");
ensureUser(udao, "operador@gmail.com", "123456", "OPERADOR_HUB");
// ... etc
```

#### Ahora (sincroniza desde API):
```java
// ✅ NUEVA ESTRATEGIA: Sincronizar usuarios desde la API (GlassFish JPA)
syncUsersFromAPI(ctx);
```

### 2.2. Nuevo método: `syncUsersFromAPI()`
Este método:
1. 📡 Se conecta a la API GlassFish
2. 📥 Descarga TODOS los usuarios (los 5 definidos en el servidor)
3. 💾 Los guarda en Room (base de datos local Android)
4. 🔄 Actualiza si ya existen, inserta si son nuevos
5. ⚠️ Si falla la conexión, crea usuarios locales como fallback

### 2.3. Mecanismo de Fallback
Si la API no está disponible, la app automáticamente:
- Crea los 5 usuarios localmente
- Permite que la app funcione sin conexión
- Muestra logs de advertencia

---

## 🔄 FLUJO DE SINCRONIZACIÓN

```
┌─────────────────────────────────────────────────────────────┐
│  1. App Android se inicia                                   │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  2. DemoSeeder.seedOnce() ejecuta                           │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  3. syncUsersFromAPI() se conecta a GlassFish               │
│     GET http://localhost:8080/EncomiendasAPI/api/users      │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  4. GlassFish devuelve JSON con los 5 usuarios              │
│     [                                                        │
│       {id: 1, email: "remitente.demo@gmail.com", ...},     │
│       {id: 2, email: "operador@gmail.com", ...},           │
│       ...                                                    │
│     ]                                                        │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  5. Android guarda/actualiza usuarios en Room               │
│     - Verifica si existe por email                          │
│     - Si existe: UPDATE                                     │
│     - Si no existe: INSERT                                  │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│  6. ✅ Usuarios disponibles para login                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 🧪 CÓMO PROBAR

### Paso 1: Verificar el servidor
```bash
# Ir a Postman o navegador
GET http://localhost:8080/EncomiendasAPI/api/users
```

**Respuesta esperada:**
```json
[
  {
    "id": 1,
    "email": "remitente.demo@gmail.com",
    "telefono": "3001234567",
    "passwordHash": "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92",
    "rol": "REMITENTE",
    "createdAt": 1731578400000
  },
  {
    "id": 2,
    "email": "operador@gmail.com",
    "telefono": "3009876543",
    "passwordHash": "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92",
    "rol": "OPERADOR_HUB",
    "createdAt": 1731578400000
  }
  // ... (total 5 usuarios)
]
```

### Paso 2: Ejecutar la app Android
1. Abre Android Studio
2. Ejecuta la app
3. Observa los logs (Logcat):

```
D/DemoSeeder: 📡 Sincronizando usuarios desde API GlassFish...
D/DemoSeeder: ✅ Descargados 5 usuarios de la API
D/DemoSeeder: ➕ Insertado: remitente.demo@gmail.com (REMITENTE)
D/DemoSeeder: ➕ Insertado: operador@gmail.com (OPERADOR_HUB)
D/DemoSeeder: ➕ Insertado: repartidor1@gmail.com (REPARTIDOR)
D/DemoSeeder: ➕ Insertado: asignador@gmail.com (ASIGNADOR)
D/DemoSeeder: ➕ Insertado: admin@gmail.com (ADMIN)
D/DemoSeeder: 💾 ✅ Usuarios sincronizados correctamente desde API
```

### Paso 3: Probar login
Intenta hacer login con cualquiera de estos usuarios:
- Email: `admin@gmail.com`
- Password: `123456`

---

## 📊 CREDENCIALES DE ACCESO

| Email                      | Password | Rol          |
|----------------------------|----------|--------------|
| remitente.demo@gmail.com   | 123456   | REMITENTE    |
| operador@gmail.com         | 123456   | OPERADOR_HUB |
| repartidor1@gmail.com      | 123456   | REPARTIDOR   |
| asignador@gmail.com        | 123456   | ASIGNADOR    |
| admin@gmail.com            | 123456   | ADMIN        |

---

## ⚠️ IMPORTANTE: Configuración de la API

### Verificar ApiClient.java
Asegúrate de que la URL base esté correcta:

```java
// app/src/main/java/com/hfad/encomiendas/api/ApiClient.java

public class ApiClient {
    // ⚠️ CAMBIAR A TU IP SI USAS DISPOSITIVO FÍSICO
    private static final String BASE_URL = "http://10.0.2.2:8080/EncomiendasAPI/api/";
    
    // Para dispositivo físico, usa algo como:
    // private static final String BASE_URL = "http://192.168.1.100:8080/EncomiendasAPI/api/";
}
```

---

## 🔍 TROUBLESHOOTING

### Problema 1: "Error conectando con API"
**Solución:**
- Verifica que GlassFish esté corriendo
- Verifica la URL en `ApiClient.java`
- Si usas emulador: usa `10.0.2.2`
- Si usas dispositivo físico: usa la IP de tu PC

### Problema 2: "No se sincronizan los usuarios"
**Solución:**
- Revisa los logs de Logcat (filtro: `DemoSeeder`)
- Verifica que `AutoSyncManager` también funcione
- Limpia la base de datos: Settings → Clear data

### Problema 3: "Login falla"
**Solución:**
- Verifica que el hash de la contraseña sea correcto
- Password `123456` debe hashear a: `8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92`

---

## ✅ CHECKLIST DE IMPLEMENTACIÓN

### Servidor GlassFish:
- [ ] Copiar nuevo UserService.java
- [ ] Reconstruir proyecto Java EE
- [ ] Desplegar en GlassFish
- [ ] Verificar endpoint `/api/users` devuelve 5 usuarios

### App Android:
- [x] DemoSeeder.java modificado (YA HECHO)
- [ ] Verificar ApiClient.java tiene URL correcta
- [ ] Ejecutar la app
- [ ] Verificar logs de sincronización
- [ ] Probar login con los 5 usuarios

---

## 📝 NOTAS ADICIONALES

1. **Persistencia JPA en el Servidor:**
   - Actualmente el servidor usa almacenamiento en memoria (List<User>)
   - Para JPA real con base de datos, necesitas configurar `persistence.xml`
   - Los usuarios se pierden al reiniciar el servidor (pero se recrean automáticamente)

2. **Sincronización Automática:**
   - `AutoSyncManager` también descarga usuarios al iniciar la app
   - Se ejecuta en background cada vez que abres la app
   - Mantiene Room sincronizado con la API

3. **Offline Support:**
   - Si la API no está disponible, la app usa usuarios locales
   - Los usuarios se crean en Room como fallback
   - La app funciona 100% offline después de la primera sincronización

---

## 🎉 RESULTADO FINAL

Después de implementar estos cambios:

✅ **Servidor GlassFish** es la fuente de verdad de usuarios
✅ **App Android** descarga usuarios automáticamente
✅ **Solo existen 5 usuarios** en el sistema
✅ **Contraseñas hasheadas** con SHA-256
✅ **Sincronización automática** al iniciar la app
✅ **Soporte offline** con fallback local

---

**¿Necesitas ayuda?** Revisa los logs con el filtro `DemoSeeder` o `AutoSyncManager`

