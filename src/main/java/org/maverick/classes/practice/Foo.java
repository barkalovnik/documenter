package org.maverick.classes.practice;
public class Foo {
  private int x;
  private int y;
  private String time;
  public Foo() {time = "always";}
  
  @Override
  public String toString() {
    return "Love(when="+time+")";
  }
}
