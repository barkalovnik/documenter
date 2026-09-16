package org.maverick;

public class Main {
    public static void main(String[] args) {
        var pokemon = new Pokemon("pikachu");

        System.out.println(pokemon.getPokemonId());
        System.out.println(pokemon.getPokemonWeight());
    }
}