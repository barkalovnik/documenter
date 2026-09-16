import java.util.Date;
import java.util.List;
import java.util.Map;

/** Демонстрация работы документатора на модели с циклическими ссылками. */
public class Demo {

    public static void main(String[] args) throws Exception {
        ClassDocumenter documenter = new ClassDocumenter()
                .setIncludeJdkClasses(false)
                .setFollowMethodSignatures(true);

        // вариант 1: по описателю класса
        documenter.documentToFile(Person.class, "Person.html");

        // вариант 2: по имени класса
        documenter.documentToFile("Order", "Order.html");

        // вариант 3: по произвольному объекту
        String html = documenter.documentObject(new Person());
        System.out.println("Размер документа для объекта Person: " + html.length() + " символов");

        System.out.println("Готово: Person.html, Order.html");
    }
}

/** Состояние заказа. */
enum Status {
    NEW, PAID, SHIPPED, CANCELLED
}

@DocInfo(value = "Почтовый адрес", author = "Иванов И.И.", since = "1.0")
class Address {
    @DocInfo("Страна")
    private String country;
    @DocInfo("Город")
    private String city;
    @DocInfo("Индекс")
    private int zip;

    public String getCity() {
        return city;
    }
}

@DocInfo(value = "Физическое лицо: покупатель или контакт", since = "1.0")
class Person {
    @DocInfo("Имя и фамилия")
    private String name;

    @DocInfo("Адрес регистрации")
    private Address address;

    @DocInfo("Адреса доставки — массив ссылок")
    private Address[] deliveryAddresses;

    @DocInfo("Друзья — циклическая ссылка на собственный класс")
    private Person[] friends;

    @DocInfo("Заказы клиента")
    private List<Order> orders;

    @DocInfo(value = "Служебные данные", deep = false)
    private Map<String, Object> attributes;

    @DocIgnore
    private String passwordHash;

    private Date registeredAt;

    public Person() {
    }

    public Person(String name, Address address) {
        this.name = name;
        this.address = address;
    }

    @DocInfo("Добавляет заказ клиенту")
    public void addOrder(Order order) {
    }

    public Address getAddress() {
        return address;
    }

    @DocIgnore
    public String getPasswordHash() {
        return passwordHash;
    }
}

@DocInfo("Заказ в интернет-магазине")
class Order {
    @DocInfo("Клиент — обратная ссылка")
    private Person customer;

    @DocInfo("Позиции заказа — массив ссылок")
    private Product[] items;

    private Status status;
    private Address shipTo;
    private double total;

    @DocInfo("Пересчитывает сумму заказа")
    public double recalculate() throws IllegalStateException {
        return total;
    }
}

@DocInfo("Товарная позиция")
class Product {
    private String title;
    private double price;
    private Category category;

    public Category getCategory() {
        return category;
    }
}

@DocInfo("Категория товара, дерево категорий")
class Category {
    private String name;
    private Category parent;
    private Category[] children;
}
