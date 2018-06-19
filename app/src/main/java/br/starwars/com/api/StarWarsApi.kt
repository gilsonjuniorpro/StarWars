package br.starwars.com.api

import android.net.Uri
import br.starwars.com.data.Movie
import br.starwars.com.data.Character
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import rx.Observable
import java.util.*

class StarWarsApi {
    val service: StarWarsApiDef

    init {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY

        val httpClient = OkHttpClient.Builder()
        httpClient.addInterceptor(logging)

        val gson = GsonBuilder().setLenient().create()

        val retrofit = Retrofit.Builder()
                .baseUrl("http://swapi.co/api/")
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(httpClient.build())
                .build()

        service = retrofit.create<StarWarsApiDef>(StarWarsApiDef::class.java)
    }

    fun loadMovies(): Observable<Movie>? {
        return service.listMovies()
                .flatMap { filmResults -> Observable.from(filmResults.results) }
                .map { film ->
                    Movie(film.title, film.episodeId, ArrayList<Character>())
                }
    }

    var peopleCache = mutableMapOf<String, Person>()

    fun loadMoviesFull(): Observable<Movie> {
        return service.listMovies()
                .flatMap { filmResults -> Observable.from(filmResults.results) }
                .flatMap { film ->
                    val movieObj = Movie(film.title, film.episodeId, ArrayList<Character>())
                    Observable.zip(
                            Observable.just(movieObj),
                            Observable.from(film.personUrls)
                                    .flatMap { personUrl ->
                                        Observable.concat(
                                                getCache(personUrl),
                                                service.loadPerson(Uri.parse(personUrl).lastPathSegment)
                                                        .doOnNext { person ->
                                                            peopleCache.put(personUrl, person)
                                                        }
                                        ).first()
                                    }
                                    .map { person ->
                                        Character(person!!.name, person.gender)
                                    }.toList(),
                            { movie, characters ->
                                movie.characters.addAll(characters)
                                movie
                            })
                }
    }

    private fun getCache(personUrl : String) : Observable<Person?>? {
        return Observable.from(peopleCache.keys)
                .filter { key ->
                    key == personUrl
                }
                .map { key ->
                    peopleCache[key]
                }
    }
}