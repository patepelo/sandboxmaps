package app.organicmaps.search;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import app.organicmaps.sdk.R;

/// Extra search categories shown under "More". Each is a single place type that no built-in category covers.
/// - label: the type's translated name (sdk types_strings.xml), shown on the tile;
/// - query: the type's main English term in data/categories.txt. Search always matches English, so this finds
///   the type in every UI language, even where the translated name isn't a search synonym;
/// - icon: the type's map icon in the symbols atlas (see MapIconCache).
final class ExtraCategories
{
  static final class Entry
  {
    @StringRes
    final int label;
    @NonNull
    final String query;
    @NonNull
    final String icon;

    Entry(@StringRes int label, @NonNull String query, @NonNull String icon)
    {
      this.label = label;
      this.query = query;
      this.icon = icon;
    }
  }

  private ExtraCategories() {}

  static final Entry[] ALL = {
      // Health and emergency
      new Entry(R.string.type_amenity_dentist, "Dentist", "dentist-m"),
      new Entry(R.string.type_amenity_veterinary, "Veterinary Doctor", "veterinary-m"),
      new Entry(R.string.type_shop_optician, "Optician", "optician-m"),
      new Entry(R.string.type_emergency_defibrillator, "Defibrillator", "defibrillator-m"),
      new Entry(R.string.type_amenity_fire_station, "Fire Station", "fire_station-m"),
      // Everyday services
      new Entry(R.string.type_shop_hairdresser, "Hairdresser", "hairdresser-m"),
      new Entry(R.string.type_shop_laundry, "Laundry", "laundry-m"),
      new Entry(R.string.type_amenity_car_wash, "Car Wash", "car-wash-m"),
      new Entry(R.string.type_amenity_car_rental, "Car rental", "car_sharing-m"),
      new Entry(R.string.type_amenity_bicycle_rental, "Bicycle rental", "bicycle-rental-xm"),
      new Entry(R.string.type_amenity_bureau_de_change, "Currency Exchange", "banknote-m"),
      new Entry(R.string.type_shop_copyshop, "Copyshop", "copyshop-m"),
      new Entry(R.string.type_craft_locksmith, "Locksmith", "key_cutter-m"),
      // Shops
      new Entry(R.string.type_shop_florist, "Florist", "florist-m"),
      new Entry(R.string.type_shop_books, "Bookstore", "book-shop-m"),
      new Entry(R.string.type_shop_doityourself, "Hardware", "doityourself-m"),
      new Entry(R.string.type_shop_bicycle, "Cycling shop", "shop-bicycle-m"),
      new Entry(R.string.type_shop_pet, "Pet", "petshop-m"),
      new Entry(R.string.type_shop_mobile_phone, "Cell Phones", "mobile_phone-m"),
      new Entry(R.string.type_shop_kiosk, "Kiosk", "kiosk-m"),
      new Entry(R.string.type_shop_outdoor, "Outdoor Equipment", "outdoor-shop-m"),
      // Public and education
      new Entry(R.string.type_amenity_library, "Library", "library-m"),
      new Entry(R.string.type_amenity_school, "School", "school-m"),
      new Entry(R.string.type_amenity_university, "University", "college-m"),
      new Entry(R.string.type_amenity_kindergarten, "Kindergarten", "kindergarten-m"),
      new Entry(R.string.type_amenity_townhall, "Town Hall", "public-building-m"),
      new Entry(R.string.type_office_diplomatic, "Embassy", "embassy-m"),
      new Entry(R.string.type_amenity_place_of_worship, "Place of Worship", "place-of-worship-m"),
      // Sport and leisure
      new Entry(R.string.type_leisure_fitness_centre, "Fitness Centre", "fitness_centre-m"),
      new Entry(R.string.type_leisure_swimming_pool, "Swimming Pool", "swimming-m"),
      new Entry(R.string.type_leisure_sports_centre, "Sports Center", "sports_centre-m"),
      new Entry(R.string.type_leisure_park, "Park", "park-m"),
      new Entry(R.string.type_leisure_dog_park, "Dog park", "dog_park-m"),
      new Entry(R.string.type_leisure_golf_course, "Golf Course", "golf-m"),
      new Entry(R.string.type_sport_climbing, "Climbing Centre", "climbing-m"),
      // Outdoors
      new Entry(R.string.type_amenity_bench, "Bench", "bench"),
      new Entry(R.string.type_amenity_fountain, "Fountain", "fountain-m"),
      new Entry(R.string.type_amenity_bicycle_parking, "Bicycle Parking", "bicycle-parking-m"),
      new Entry(R.string.type_tourism_information, "Tourist Information", "information-m"),
      new Entry(R.string.type_natural_peak, "Peak", "peakt-m"),
  };
}
