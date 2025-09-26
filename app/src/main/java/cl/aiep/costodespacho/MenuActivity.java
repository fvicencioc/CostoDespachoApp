package cl.aiep.costodespacho;

import android.Manifest;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import android.content.pm.PackageManager;

import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MenuActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private FusedLocationProviderClient fusedLocationClient;
    private CardView cardUbicaciones;

    private EditText editMonto, editTemperatura;
    private TextView textNombre, textAdvertencia, textResultado;
    private Button btnCalcular, btnLimpiar;

    private Button btnGuardarUbicacion;
    private TextView textUbicacionActual, textUbicacionBodega;

    private double latUsuario = 0.0, lonUsuario = 0.0;
    private final double LAT_BODEGA = -39.81579939602124;
    private final double LON_BODEGA = -73.24541426531084;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_menu);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        editMonto = findViewById(R.id.editMontoCompra);
        editTemperatura = findViewById(R.id.editTemperatura);
        textNombre = findViewById(R.id.textNombreUsuario);
        textAdvertencia = findViewById(R.id.textAdvertencia);
        btnCalcular = findViewById(R.id.btnCalcular);
        btnLimpiar = findViewById(R.id.btnLimpiar);
        textResultado = findViewById(R.id.textResultado);
        btnGuardarUbicacion = findViewById(R.id.btnGuardarUbicacion);
        textUbicacionActual = findViewById(R.id.textUbicacionActual);
        textUbicacionBodega = findViewById(R.id.textUbicacionBodega);
        cardUbicaciones = findViewById(R.id.cardUbicaciones);

        String nombreUsuario = firebaseAuth.getCurrentUser().getEmail();
        textNombre.setText("Bienvenido: " + nombreUsuario);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    1001); // código de solicitud
        } else {
            obtenerUbicacionYGuardar(); // ya tiene permisos
        }

        btnCalcular.setOnClickListener(v -> calcularCostoEnvio());
        btnLimpiar.setOnClickListener(v -> limpiarCampos());

        btnGuardarUbicacion.setOnClickListener(v -> {
            if (latUsuario == 0.0 && lonUsuario == 0.0) {
                Toast.makeText(this, "Ubicación aún no disponible", Toast.LENGTH_SHORT).show();
                return;
            }

            // Mostrar en pantalla
            textUbicacionActual.setText("Ubicación actual: " + latUsuario + ", " + lonUsuario);
            textUbicacionBodega.setText("Bodega: " + LAT_BODEGA + ", " + LON_BODEGA);

            // Guardar en Firebase
            String userId = firebaseAuth.getCurrentUser().getUid();
            firebaseDatabase.getReference("usuarios").child(userId).child("ubicacion_actual")
                    .setValue(latUsuario + "," + lonUsuario);

            firebaseDatabase.getReference("usuarios").child(userId).child("ubicacion_bodega")
                    .setValue(LAT_BODEGA + "," + LON_BODEGA);
            cardUbicaciones.setVisibility(CardView.VISIBLE);

            Toast.makeText(this, "Ubicación guardada correctamente", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                obtenerUbicacionYGuardar();
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void obtenerUbicacionYGuardar() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        latUsuario = location.getLatitude();
                        lonUsuario = location.getLongitude();

                        String userId = firebaseAuth.getCurrentUser().getUid();
                        firebaseDatabase.getReference("usuarios").child(userId)
                                .child("ubicacion")
                                .setValue(latUsuario + "," + lonUsuario);
                    }
                });
    }

    private void calcularCostoEnvio() {
        String montoTexto = editMonto.getText().toString().trim();
        String temperaturaTexto = editTemperatura.getText().toString().trim();

        if (montoTexto.isEmpty() || temperaturaTexto.isEmpty()) {
            Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double monto = Double.parseDouble(montoTexto);
            double temperatura = Double.parseDouble(temperaturaTexto);

            if (latUsuario == 0.0 && lonUsuario == 0.0) {
                Toast.makeText(this, "Ubicación no disponible aún", Toast.LENGTH_SHORT).show();
                return;
            }

            if (temperatura > 4) {
                textAdvertencia.setText("Advertencia: Temperatura supera los 4°C");
            } else {
                textAdvertencia.setText("");
            }

            double distancia = calcularDistancia(LAT_BODEGA, LON_BODEGA, latUsuario, lonUsuario);
            double costo = calcularCosto(monto, distancia);

            textResultado.setText("Costo de envío: $" + Math.round(costo));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Ingresa valores numéricos válidos", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error inesperado: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Radio de la Tierra en kilómetros

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(radLat1) * Math.cos(radLat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // distancia en kilómetros
    }

    private double calcularCosto(double monto, double distanciaKm) {
        if (monto >= 50000) {
            if (distanciaKm <= 20) return 0;
            else return (distanciaKm - 20) * 150;
        } else if (monto >= 25000) {
            return distanciaKm * 150;
        } else {
            return distanciaKm * 300;
        }
    }

    private void limpiarCampos() {
        editMonto.setText("");
        editTemperatura.setText("");
        textAdvertencia.setText("");
        textResultado.setText("");
    }
}