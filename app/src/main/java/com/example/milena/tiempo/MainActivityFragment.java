package com.example.milena.tiempo;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivityFragment extends Fragment {
    ArrayAdapter<String> adaptador=null;
    HttpURLConnection urlConnection = null;
    BufferedReader reader = null;


    // Contendrá la respuesta JSON en una cadena.
    String forecastJsonStr = null;


    public MainActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

//  esto agrega elementos a al action bar si está presente .
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

//codigo postal sibundoy
        if (id == R.id.action_update) {
            FetchWeatherTask weatherTask = new FetchWeatherTask();
            weatherTask.execute("861020");

            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        String[] data = {

                "Mon 6/23 - Sunny - 31/17",
                "Tue 6/24 - Foggy - 21/8",
                "Wed 6/25 - Cloudy - 22/17",
                "Thurs 6/26 - Rainy - 18/11",
                "Fri 6/27 - Foggy - 21/10",
                "Sat 6/28 - TRAPPED IN WEATHERSTATION - 23/18",
                "Sun 6/29 - Sunny - 20/7"};
        List<String> weekForecast = new ArrayList<String>(Arrays.asList(data));

            adaptador =
                new ArrayAdapter<String>(
                        getActivity(),// El contexto actual ( esta actividad )
                        R.layout.list_item_forecast,// El nombre del ID de diseño .
                        R.id.list_item_forecast_textview,// El ID de la TextView para poblar
                        weekForecast);

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

// Obtener una referencia a la ListView , y conecte este adaptador a la misma.
        ListView listView = (ListView) rootView.findViewById(R.id.listView);
        listView.setAdapter(adaptador);
               return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String,Void,String[]> {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

            private String getReadableDateString(long time){

// Debido a que el API devuelve una marca de tiempo Unix ( medido en segundos ) ,
                // Debe ser convertido a milisegundos con el fin de convertir en fecha válida.

            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("'DIAS:' EEEEEEEEE dd 'de' MMMMM 'de' yyyy");
            return shortenedDateFormat.format(time);
        }

        /*preparar el clima
                maximos/minimos para la presentación.
        */

        private String formatHighLows(double high, double low) {

// Para la presentación , se supone que el usuario no se preocupa por décimas de grado .
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);
            roundedHigh-=273;
            roundedLow-=273;

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }
        /* Tome la cadena que representa el pronóstico completo en formato JSON y Extraiga los datos que necesitamos
         para construir las cadenas necesarios para las estructuras alámbricas .
         Afortunadamente análisis es fácil: constructor toma la cadena JSON y lo convierte
          En una jerarquía de objetos para nosotros .
                */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

// Estos son los nombres de los objetos JSON que necesitan ser extraídos .
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

// devuelve OWM previsiones diarias basadas en la hora local de la ciudad que está siendo
            // Pedimos, lo que significa que es necesario conocer el desplazamiento de traducir estos datos GMT
            // Correctamente.

            // Desde estos datos también se envía en el orden y la primera día es siempre el
            // Día actual , vamos a tomar ventaja de eso para conseguir un buen
            // Normalizado fecha UTC para todos los fenómenos meteorológicos .
            Time dayTime = new Time();
            dayTime.setToNow();


// Empezamos por el día devuelto por hora local. De lo contrario, esto es un desastre .
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);
// Ahora trabajamos exclusivamente en UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for(int i = 0; i < weatherArray.length(); i++) {

// Por ahora , utilizando el formato " day, descriptión, highAndLow "

                String day;
                String description;
                String highAndLow;

// Obtener el objeto JSON que representa el día

                JSONObject dayForecast = weatherArray.getJSONObject(i);
// La fecha / hora se devuelve como mucho . Tenemos que convertir ese
                // En algo legible por humanos , ya que la mayoría de la gente no va a leer " 1400356800 ", como
                // "este sábado".
                long dateTime;



// Hacer trampa de convertir esto a la hora UTC , que es lo que queremos todos modos
                dateTime = dayTime.setJulianDay(julianStartDay+i);
                day = getReadableDateString(dateTime);
                // description is in a child array called "weather", which is 1 element long.

                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                //  Las temperaturas están en un objeto secundario llamado " temp" . Trate de no nombrar las variables
                // " Temp" cuando se trabaja con la temperatura. Se confunde a todo el mundo.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            for (String s : resultStrs) {
                Log.v(LOG_TAG, "Forecast entry: " + s);
            }
            return resultStrs;

        }

        @Override
        protected String[] doInBackground(String... params) {

            if (params.length == 0) {
                return null;
            }

// Fin de que puedan ser cerradas en el bloque finally .
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;


// Contendrá la respuesta JSON  como una cadena
            String forecastJsonStr = null;
            String format = "json";
            String units ="units";
            int numDays=10;

            try {
                String baseUrl = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNIST_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                Uri builUri = Uri.parse(baseUrl).buildUpon()
                        .appendQueryParameter(QUERY_PARAM,params[0])
                        .appendQueryParameter(FORMAT_PARAM,format)
                        .appendQueryParameter(UNIST_PARAM,units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .build();

                String apiKey = "&APPID=" + BuildConfig.OPEN_WEATHER_MAP_API_KEY;
                URL url = new URL(builUri.toString().concat(apiKey));

// Crear la solicitud de OpenWeatherMap , y abrir la conexión
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();


// Lea la secuencia de entrada en una cadena
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    forecastJsonStr = null;
                   // Toast.makeText(  getActivity(),"no hay coneccion a internet att:milena",Toast.LENGTH_SHORT).show();



// Nada que hacer.
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {

// Ya que es JSON , la adición de una nueva línea no es necesario ( que no afectará el análisis sintáctico )
                    // Modos hace que la depuración mucho más fácil * * si imprime el completado
                    // Búfer para la depuración.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {

// Corriente estaba vacío . No hay punto en el análisis sintáctico .
                    forecastJsonStr = null;
                }
                forecastJsonStr = buffer.toString();

            } catch (IOException e) {
                Log.e("PlaceholderFragment", "Error ", e);

// Si el código no consiguió con éxito los datos del tiempo , no hay ningún punto en attemping
                // Para analizarlo.
                forecastJsonStr = null;
            } finally{
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("PlaceholderFragment", "Error closing stream", e);
                    }
                }
            }
            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

// Esto sólo ocurrirá si se produjo un error al obtener o analizar el pronóstico
            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (result != null) {
                adaptador.clear();
                for(String dayForecastStr : result) {
                    adaptador.add(dayForecastStr);
                }
                // Los nuevos datos se encuentre de nuevo desde el servidor . Hooray !
            }

        }

        }

}


