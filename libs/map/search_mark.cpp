#include "map/search_mark.hpp"

#include "map/bookmark_manager.hpp"

#include "drape_frontend/drape_engine.hpp"
#include "drape_frontend/render_state_extension.hpp"
#include "drape_frontend/user_marks_provider.hpp"

#include "indexer/classificator.hpp"
#include "indexer/scales.hpp"

#include "platform/platform.hpp"

#include "geometry/screenbase.hpp"

#include "base/assert.hpp"
#include "base/control_flow.hpp"
#include "base/stl_helpers.hpp"

#include <algorithm>
#include <array>

enum SearchMarkPoint::SearchMarkType : uint8_t
{
  Default = 0,
  Hotel,
  Hostel,
  Chalet,
  Apartment,
  Campsite,
  CaravanSite,
  Cafe,
  Bakery,
  Bar,
  Pub,
  Restaurant,
  FastFood,
  Casino,
  Lottery,
  Cinema,
  Theatre,
  Stadium,
  Museum,
  Art,
  Attraction,
  Viewpoint,
  Remains,
  ArchaeologicalSite,
  Information,
  Marketplace,
  Nightclub,
  Playground,
  Bank,
  Fuel,
  ChargingStation,
  ShopAlcohol,
  ShopButcher,
  ShopClothes,
  ShopConfectionery,
  ShopConvenience,
  ShopBeauty,
  ShopDepartmentStore,
  ShopGift,
  ShopGreengrocer,
  ShopJewelry,
  ShopSeafood,
  ShopShoes,
  ShopSports,
  ShopSupermarket,
  ShopToys,
  ThemePark,
  Zoo,
  Pitch,
  Swimming,
  Hospital,
  Clinic,
  Pharmacy,
  DrinkingWater,
  DrinkingWaterNo,
  BicycleParking,
  BicycleParkingCovered,
  BicycleRental,
  Toilets,
  Atm,
  Parking,
  Police,
  PostOffice,
  PostBox,
  Dentist,
  Veterinary,
  Defibrillator,
  FireStation,
  Library,
  School,
  University,
  Kindergarten,
  PlaceOfWorship,
  Townhall,
  CommunityCentre,
  BusStation,
  TrainStation,
  Taxi,
  CarRental,
  CarWash,
  FerryTerminal,
  Airport,
  BureauDeChange,
  ShopHairdresser,
  ShopLaundry,
  ShopBooks,
  ShopElectronics,
  ShopDoItYourself,
  ShopFlorist,
  ShopOptician,
  ShopMobilePhone,
  ShopPet,
  ShopKiosk,
  ShopBicycle,
  ShopCarRepair,
  ShopFurniture,
  ShopMall,
  IceCream,
  FitnessCentre,
  Park,
  Golf,
  Marina,
  Peak,
  Waterfall,
  Cave,
  Monument,
  PicnicSite,
  Shelter,
  Bench,
  Recycling,
  VendingMachine,
  Telephone,
  MotorcycleParking,
  BicycleRepairStation,
  InternetCafe,
  ShopTobacco,
  ShopNewsagent,
  ShopSecondHand,
  ShopChemist,
  ShopTicket,
  ShopCopyshop,
  ShopPhoto,
  ShopMusic,
  ShopTattoo,
  ShopGardenCentre,
  ShopStationery,
  DogPark,
  Sauna,
  MiniatureGolf,
  BowlingAlley,
  NursingHome,

  NotFound,  // Service value used in developer tools.
  Count
};

using SearchMarkType = SearchMarkPoint::SearchMarkType;

namespace
{
df::ColorConstant const kColorConstant = "SearchmarkDefault";

float constexpr kVisitedSymbolOpacity = 0.7f;
float constexpr kOutOfFiltersSymbolOpacity = 0.4f;

std::array<std::string, SearchMarkType::Count> const kSymbols = {
    "search-result",                          // Default.
    "search-result-hotel",                    // Hotel.
    "search-result-hostel",                   // Hostel.
    "search-result-chalet",                   // Chalet.
    "search-result-apartment",                // Apartment.
    "search-result-campsite",                 // Campsite.
    "search-result-caravan-site",             // Caravan site.
    "search-result-cafe",                     // Cafe.
    "search-result-bakery",                   // Bakery.
    "search-result-bar",                      // Bar.
    "search-result-pub",                      // Pub.
    "search-result-restaurant",               // Restaurant.
    "search-result-fastfood",                 // FastFood.
    "search-result-casino",                   // Casino.
    "search-result-lottery",                  // Lottery.
    "search-result-cinema",                   // Cinema.
    "search-result-theatre",                  // Theatre.
    "search-result-stadium",                  // Stadium.
    "search-result-museum",                   // Museum.
    "search-result-art",                      // Art.
    "search-result-attraction",               // Attraction.
    "search-result-viewpoint",                // Viewpoint.
    "search-result-remains",                  // Remains.
    "search-result-archaeological-site",      // ArchaeologicalSite.
    "search-result-information",              // Information.
    "search-result-marketplace",              // Marketplace.
    "search-result-nightclub",                // Nightclub.
    "search-result-playground",               // Playground.
    "search-result-bank",                     // Bank.
    "search-result-fuel",                     // Fuel.
    "search-result-charging_station",         // ChargingStation.
    "search-result-shop-alcohol",             // ShopAlcohol.
    "search-result-shop-butcher",             // ShopButcher.
    "search-result-shop-clothes",             // ShopClothes.
    "search-result-shop-confectionery",       // ShopConfectionery.
    "search-result-shop-convenience",         // ShopConvenience.
    "search-result-shop-beauty",              // ShopBeauty.
    "search-result-shop-department_store",    // ShopDepartmentStore.
    "search-result-shop-gift",                // ShopGift.
    "search-result-shop-greengrocer",         // ShopGreengrocer.
    "search-result-shop-jewelry",             // ShopJewelry.
    "search-result-shop-seafood",             // ShopSeafood.
    "search-result-shop-shoes",               // ShopShoes.
    "search-result-shop-sports",              // ShopSports.
    "search-result-shop-supermarket",         // ShopSupermarket.
    "search-result-shop-toys",                // ShopToys.
    "search-result-theme-park",               // ThemePark.
    "search-result-zoo",                      // Zoo.
    "search-result-pitch",                    // Pitch.
    "search-result-swimming",                 // Swimming.
    "search-result-hospital",                 // Hospital.
    "search-result-clinic",                   // Clinic.
    "search-result-pharmacy",                 // Pharmacy.
    "search-result-drinking-water",           // DrinkingWater.
    "search-result-drinking-water-no",        // DrinkingWaterNo.
    "search-result-bicycle_parking",          // BicycleParking.
    "search-result-bicycle_parking-covered",  // BicycleParkingCovered.
    "search-result-bicycle_rental",           // BicycleRental.

    "search-result-toilets",
    "search-result-atm",
    "search-result-parking",
    "search-result-police",
    "search-result-post_office",
    "search-result-post_box",
    "search-result-dentist",
    "search-result-veterinary",
    "search-result-defibrillator",
    "search-result-fire_station",
    "search-result-library",
    "search-result-school",
    "search-result-university",
    "search-result-kindergarten",
    "search-result-place_of_worship",
    "search-result-townhall",
    "search-result-community_centre",
    "search-result-bus_station",
    "search-result-train_station",
    "search-result-taxi",
    "search-result-car_rental",
    "search-result-car_wash",
    "search-result-ferry_terminal",
    "search-result-airport",
    "search-result-bureau_de_change",
    "search-result-shop-hairdresser",
    "search-result-shop-laundry",
    "search-result-shop-books",
    "search-result-shop-electronics",
    "search-result-shop-doityourself",
    "search-result-shop-florist",
    "search-result-shop-optician",
    "search-result-shop-mobile_phone",
    "search-result-shop-pet",
    "search-result-shop-kiosk",
    "search-result-shop-bicycle",
    "search-result-shop-car_repair",
    "search-result-shop-furniture",
    "search-result-shop-mall",
    "search-result-ice_cream",
    "search-result-fitness_centre",
    "search-result-park",
    "search-result-golf",
    "search-result-marina",
    "search-result-peak",
    "search-result-waterfall",
    "search-result-cave",
    "search-result-monument",
    "search-result-picnic_site",
    "search-result-shelter",
    "search-result-bench",
    "search-result-recycling",
    "search-result-vending_machine",
    "search-result-telephone",
    "search-result-motorcycle_parking",
    "search-result-bicycle_repair_station",
    "search-result-internet_cafe",
    "search-result-shop-tobacco",
    "search-result-shop-newsagent",
    "search-result-shop-second_hand",
    "search-result-shop-chemist",
    "search-result-shop-ticket",
    "search-result-shop-copyshop",
    "search-result-shop-photo",
    "search-result-shop-music",
    "search-result-shop-tattoo",
    "search-result-shop-garden_centre",
    "search-result-shop-stationery",
    "search-result-dog_park",
    "search-result-sauna",
    "search-result-miniature_golf",
    "search-result-bowling_alley",
    "search-result-nursing_home",
    "search-result-non-found",  // NotFound.
};

std::string const & GetSymbol(SearchMarkType searchMarkType)
{
  ASSERT_LESS(searchMarkType, kSymbols.size(), ());
  return kSymbols[searchMarkType];
}

class SearchMarkTypeChecker
{
public:
  static SearchMarkTypeChecker & Instance()
  {
    static SearchMarkTypeChecker checker;
    return checker;
  }

  SearchMarkType GetSearchMarkType(uint32_t type) const
  {
    for (uint8_t level = ftype::GetLevel(type); level > 0; --level)
    {
      uint32_t const t = ftype::Trunc(type, level);
      auto const it = std::partition_point(m_searchMarkTypes.cbegin(), m_searchMarkTypes.cend(),
                                           [t](auto && p) { return p.first < t; });
      if (it != m_searchMarkTypes.cend() && it->first == t)
        return it->second;
    }
    return SearchMarkType::Default;
  }

private:
  using Type = std::pair<uint32_t, SearchMarkType>;

  SearchMarkTypeChecker()
  {
    auto const & c = classif();
    std::pair<std::vector<std::string_view>, SearchMarkType> const table[] = {
        {{"amenity", "cafe"}, SearchMarkType::Cafe},
        {{"shop", "bakery"}, SearchMarkType::Bakery},
        {{"shop", "pastry"}, SearchMarkType::Bakery},
        {{"amenity", "bar"}, SearchMarkType::Bar},
        {{"amenity", "pub"}, SearchMarkType::Pub},
        {{"amenity", "biergarten"}, SearchMarkType::Pub},
        {{"amenity", "restaurant"}, SearchMarkType::Restaurant},
        {{"amenity", "food_court"}, SearchMarkType::Restaurant},
        {{"amenity", "fast_food"}, SearchMarkType::FastFood},
        {{"amenity", "casino"}, SearchMarkType::Casino},
        {{"shop", "bookmaker"}, SearchMarkType::Lottery},
        {{"shop", "lottery"}, SearchMarkType::Lottery},
        {{"amenity", "cinema"}, SearchMarkType::Cinema},
        {{"amenity", "theatre"}, SearchMarkType::Theatre},
        {{"leisure", "stadium"}, SearchMarkType::Stadium},
        {{"tourism", "museum"}, SearchMarkType::Museum},
        {{"amenity", "arts_centre"}, SearchMarkType::Art},
        {{"tourism", "gallery"}, SearchMarkType::Art},
        {{"tourism", "attraction"}, SearchMarkType::Attraction},
        {{"tourism", "viewpoint"}, SearchMarkType::Viewpoint},
        {{"historic", "fort"}, SearchMarkType::Remains},
        {{"historic", "castle"}, SearchMarkType::Remains},
        {{"historic", "castle", "castrum"}, SearchMarkType::Remains},
        {{"historic", "castle", "fortified_church"}, SearchMarkType::Remains},
        {{"historic", "castle", "fortress"}, SearchMarkType::Remains},
        {{"historic", "castle", "hillfort"}, SearchMarkType::Remains},
        {{"historic", "castle", "kremlin"}, SearchMarkType::Remains},
        {{"historic", "castle", "manor"}, SearchMarkType::Remains},
        {{"historic", "castle", "palace"}, SearchMarkType::Remains},
        {{"historic", "castle", "shiro"}, SearchMarkType::Remains},
        {{"historic", "castle", "defensive"}, SearchMarkType::Remains},
        {{"historic", "castle", "stately"}, SearchMarkType::Remains},
        {{"historic", "ruins"}, SearchMarkType::Remains},
        {{"historic", "city_gate"}, SearchMarkType::Remains},
        {{"historic", "archaeological_site"}, SearchMarkType::ArchaeologicalSite},
        {{"tourism", "information"}, SearchMarkType::Information},
        {{"tourism", "information", "office"}, SearchMarkType::Information},
        {{"tourism", "information", "visitor_centre"}, SearchMarkType::Information},
        {{"amenity", "marketplace"}, SearchMarkType::Marketplace},
        {{"amenity", "nightclub"}, SearchMarkType::Nightclub},
        {{"leisure", "playground"}, SearchMarkType::Playground},
        {{"amenity", "bank"}, SearchMarkType::Bank},
        {{"shop", "money_lender"}, SearchMarkType::Bank},
        {{"amenity", "fuel"}, SearchMarkType::Fuel},
        {{"amenity", "charging_station"}, SearchMarkType::ChargingStation},
        {{"amenity", "charging_station", "bicycle"}, SearchMarkType::ChargingStation},
        {{"amenity", "charging_station", "motorcycle"}, SearchMarkType::ChargingStation},
        {{"amenity", "charging_station", "motorcar"}, SearchMarkType::ChargingStation},
        {{"shop", "alcohol"}, SearchMarkType::ShopAlcohol},
        {{"shop", "beverages"}, SearchMarkType::ShopAlcohol},
        {{"shop", "wine"}, SearchMarkType::ShopAlcohol},
        {{"shop", "butcher"}, SearchMarkType::ShopButcher},
        {{"shop", "clothes"}, SearchMarkType::ShopClothes},
        {{"shop", "confectionery"}, SearchMarkType::ShopConfectionery},
        {{"shop", "chocolate"}, SearchMarkType::ShopConfectionery},
        {{"craft", "confectionery"}, SearchMarkType::ShopConfectionery},
        {{"shop", "convenience"}, SearchMarkType::ShopConvenience},
        {{"shop", "grocery"}, SearchMarkType::ShopConvenience},
        {{"shop", "deli"}, SearchMarkType::ShopConvenience},
        {{"shop", "farm"}, SearchMarkType::ShopConvenience},
        {{"shop", "health_food"}, SearchMarkType::ShopConvenience},
        {{"shop", "beauty"}, SearchMarkType::ShopBeauty},
        {{"shop", "cosmetics"}, SearchMarkType::ShopBeauty},
        {{"shop", "department_store"}, SearchMarkType::ShopDepartmentStore},
        {{"shop", "gift"}, SearchMarkType::ShopGift},
        {{"shop", "greengrocer"}, SearchMarkType::ShopGreengrocer},
        {{"shop", "jewelry"}, SearchMarkType::ShopJewelry},
        {{"shop", "seafood"}, SearchMarkType::ShopSeafood},
        {{"shop", "shoes"}, SearchMarkType::ShopShoes},
        {{"craft", "shoemaker"}, SearchMarkType::ShopShoes},
        {{"shop", "sports"}, SearchMarkType::ShopSports},
        {{"shop", "supermarket"}, SearchMarkType::ShopSupermarket},
        {{"shop", "toys"}, SearchMarkType::ShopToys},
        {{"tourism", "theme_park"}, SearchMarkType::ThemePark},
        {{"tourism", "zoo"}, SearchMarkType::Zoo},
        {{"tourism", "chalet"}, SearchMarkType::Chalet},
        {{"tourism", "alpine_hut"}, SearchMarkType::Chalet},
        {{"tourism", "wilderness_hut"}, SearchMarkType::Chalet},
        {{"tourism", "hotel"}, SearchMarkType::Hotel},
        {{"tourism", "motel"}, SearchMarkType::Hotel},
        {{"leisure", "resort"}, SearchMarkType::Hotel},
        {{"tourism", "hostel"}, SearchMarkType::Hostel},
        {{"tourism", "apartment"}, SearchMarkType::Apartment},
        {{"tourism", "guest_house"}, SearchMarkType::Apartment},
        {{"tourism", "camp_site"}, SearchMarkType::Campsite},
        {{"tourism", "caravan_site"}, SearchMarkType::CaravanSite},
        {{"amenity", "hospital"}, SearchMarkType::Hospital},
        {{"amenity", "clinic"}, SearchMarkType::Clinic},
        {{"amenity", "doctors"}, SearchMarkType::Clinic},
        {{"amenity", "pharmacy"}, SearchMarkType::Pharmacy},
        {{"leisure", "pitch"}, SearchMarkType::Pitch},
        {{"leisure", "ice_rink"}, SearchMarkType::Pitch},
        {{"leisure", "sports_centre"}, SearchMarkType::Pitch},
        {{"leisure", "sports_hall"}, SearchMarkType::Pitch},
        {{"leisure", "swimming_pool"}, SearchMarkType::Swimming},
        {{"leisure", "water_park"}, SearchMarkType::Swimming},
        {{"amenity", "drinking_water"}, SearchMarkType::DrinkingWater},
        {{"amenity", "water_point"}, SearchMarkType::DrinkingWater},
        {{"man_made", "water_tap"}, SearchMarkType::DrinkingWater},
        {{"man_made", "water_well"}, SearchMarkType::DrinkingWater},
        {{"natural", "spring"}, SearchMarkType::DrinkingWater},
        {{"natural", "hot_spring"}, SearchMarkType::DrinkingWater},
        {{"amenity", "water_point", "drinking_water_no"}, SearchMarkType::DrinkingWaterNo},
        {{"man_made", "water_tap", "drinking_water_no"}, SearchMarkType::DrinkingWaterNo},
        {{"man_made", "water_well", "drinking_water_no"}, SearchMarkType::DrinkingWaterNo},
        {{"natural", "spring", "drinking_water_no"}, SearchMarkType::DrinkingWaterNo},
        {{"amenity", "bicycle_parking"}, SearchMarkType::BicycleParking},
        {{"amenity", "bicycle_parking", "covered"}, SearchMarkType::BicycleParkingCovered},
        {{"amenity", "bicycle_rental"}, SearchMarkType::BicycleRental},
        {{"amenity", "toilets"}, SearchMarkType::Toilets},
        {{"amenity", "toilets", "customers"}, SearchMarkType::Toilets},
        {{"amenity", "toilets", "private"}, SearchMarkType::Toilets},
        {{"amenity", "atm"}, SearchMarkType::Atm},
        {{"amenity", "parking"}, SearchMarkType::Parking},
        {{"amenity", "parking", "customers"}, SearchMarkType::Parking},
        {{"amenity", "parking", "fee"}, SearchMarkType::Parking},
        {{"amenity", "parking", "lane"}, SearchMarkType::Parking},
        {{"amenity", "parking", "lane", "fee"}, SearchMarkType::Parking},
        {{"amenity", "parking", "lane", "private"}, SearchMarkType::Parking},
        {{"amenity", "parking", "multi-storey"}, SearchMarkType::Parking},
        {{"amenity", "parking", "multi-storey", "fee"}, SearchMarkType::Parking},
        {{"amenity", "parking", "no-access"}, SearchMarkType::Parking},
        {{"amenity", "parking", "park_and_ride"}, SearchMarkType::Parking},
        {{"amenity", "parking", "permissive"}, SearchMarkType::Parking},
        {{"amenity", "parking", "private"}, SearchMarkType::Parking},
        {{"amenity", "parking", "street_side"}, SearchMarkType::Parking},
        {{"amenity", "parking", "street_side", "fee"}, SearchMarkType::Parking},
        {{"amenity", "parking", "street_side", "private"}, SearchMarkType::Parking},
        {{"amenity", "parking", "underground"}, SearchMarkType::Parking},
        {{"amenity", "parking", "underground", "fee"}, SearchMarkType::Parking},
        {{"amenity", "parking", "underground", "private"}, SearchMarkType::Parking},
        {{"amenity", "police"}, SearchMarkType::Police},
        {{"amenity", "post_office"}, SearchMarkType::PostOffice},
        {{"amenity", "post_box"}, SearchMarkType::PostBox},
        {{"amenity", "dentist"}, SearchMarkType::Dentist},
        {{"amenity", "veterinary"}, SearchMarkType::Veterinary},
        {{"emergency", "defibrillator"}, SearchMarkType::Defibrillator},
        {{"amenity", "fire_station"}, SearchMarkType::FireStation},
        {{"amenity", "library"}, SearchMarkType::Library},
        {{"amenity", "school"}, SearchMarkType::School},
        {{"amenity", "university"}, SearchMarkType::University},
        {{"amenity", "college"}, SearchMarkType::University},
        {{"amenity", "kindergarten"}, SearchMarkType::Kindergarten},
        {{"amenity", "place_of_worship"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "place_of_worship", "buddhist"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "place_of_worship", "christian"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "place_of_worship", "christian", "jehovahs_witness"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "place_of_worship", "christian", "mormon"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "place_of_worship", "hindu"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "place_of_worship", "jewish"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "place_of_worship", "muslim"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "place_of_worship", "shinto"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "place_of_worship", "taoist"}, SearchMarkType::PlaceOfWorship},
        {{"amenity", "townhall"}, SearchMarkType::Townhall},
        {{"amenity", "community_centre"}, SearchMarkType::CommunityCentre},
        {{"amenity", "community_centre", "youth_centre"}, SearchMarkType::CommunityCentre},
        {{"amenity", "bus_station"}, SearchMarkType::BusStation},
        {{"railway", "station"}, SearchMarkType::TrainStation},
        {{"railway", "station", "funicular"}, SearchMarkType::TrainStation},
        {{"railway", "station", "light_rail"}, SearchMarkType::TrainStation},
        {{"railway", "station", "light_rail", "berlin"}, SearchMarkType::TrainStation},
        {{"railway", "station", "light_rail", "london"}, SearchMarkType::TrainStation},
        {{"railway", "station", "light_rail", "porto"}, SearchMarkType::TrainStation},
        {{"railway", "station", "monorail"}, SearchMarkType::TrainStation},
        {{"amenity", "taxi"}, SearchMarkType::Taxi},
        {{"amenity", "car_rental"}, SearchMarkType::CarRental},
        {{"amenity", "car_wash"}, SearchMarkType::CarWash},
        {{"amenity", "ferry_terminal"}, SearchMarkType::FerryTerminal},
        {{"aeroway", "aerodrome"}, SearchMarkType::Airport},
        {{"aeroway", "aerodrome", "international"}, SearchMarkType::Airport},
        {{"amenity", "bureau_de_change"}, SearchMarkType::BureauDeChange},
        {{"shop", "hairdresser"}, SearchMarkType::ShopHairdresser},
        {{"shop", "laundry"}, SearchMarkType::ShopLaundry},
        {{"shop", "books"}, SearchMarkType::ShopBooks},
        {{"shop", "electronics"}, SearchMarkType::ShopElectronics},
        {{"shop", "doityourself"}, SearchMarkType::ShopDoItYourself},
        {{"shop", "hardware"}, SearchMarkType::ShopDoItYourself},
        {{"shop", "florist"}, SearchMarkType::ShopFlorist},
        {{"shop", "optician"}, SearchMarkType::ShopOptician},
        {{"shop", "mobile_phone"}, SearchMarkType::ShopMobilePhone},
        {{"shop", "pet"}, SearchMarkType::ShopPet},
        {{"shop", "kiosk"}, SearchMarkType::ShopKiosk},
        {{"shop", "bicycle"}, SearchMarkType::ShopBicycle},
        {{"shop", "car_repair"}, SearchMarkType::ShopCarRepair},
        {{"shop", "car_repair", "tyres"}, SearchMarkType::ShopCarRepair},
        {{"shop", "furniture"}, SearchMarkType::ShopFurniture},
        {{"shop", "mall"}, SearchMarkType::ShopMall},
        {{"amenity", "ice_cream"}, SearchMarkType::IceCream},
        {{"leisure", "fitness_centre"}, SearchMarkType::FitnessCentre},
        {{"leisure", "fitness_centre", "sport", "yoga"}, SearchMarkType::FitnessCentre},
        {{"leisure", "park"}, SearchMarkType::Park},
        {{"leisure", "park", "no-access"}, SearchMarkType::Park},
        {{"leisure", "park", "permissive"}, SearchMarkType::Park},
        {{"leisure", "park", "private"}, SearchMarkType::Park},
        {{"leisure", "golf_course"}, SearchMarkType::Golf},
        {{"leisure", "marina"}, SearchMarkType::Marina},
        {{"natural", "peak"}, SearchMarkType::Peak},
        {{"waterway", "waterfall"}, SearchMarkType::Waterfall},
        {{"natural", "cave_entrance"}, SearchMarkType::Cave},
        {{"historic", "monument"}, SearchMarkType::Monument},
        {{"historic", "memorial"}, SearchMarkType::Monument},
        {{"tourism", "picnic_site"}, SearchMarkType::PicnicSite},
        {{"amenity", "shelter"}, SearchMarkType::Shelter},
        {{"amenity", "shelter", "basic_hut"}, SearchMarkType::Shelter},
        {{"amenity", "shelter", "lean_to"}, SearchMarkType::Shelter},
        {{"amenity", "bench"}, SearchMarkType::Bench},
        {{"amenity", "recycling", "centre"}, SearchMarkType::Recycling},
        {{"amenity", "recycling", "container"}, SearchMarkType::Recycling},
        {{"amenity", "vending_machine"}, SearchMarkType::VendingMachine},
        {{"amenity", "vending_machine", "cigarettes"}, SearchMarkType::VendingMachine},
        {{"amenity", "vending_machine", "coffee"}, SearchMarkType::VendingMachine},
        {{"amenity", "vending_machine", "condoms"}, SearchMarkType::VendingMachine},
        {{"amenity", "vending_machine", "drinks"}, SearchMarkType::VendingMachine},
        {{"amenity", "vending_machine", "food"}, SearchMarkType::VendingMachine},
        {{"amenity", "vending_machine", "newspapers"}, SearchMarkType::VendingMachine},
        {{"amenity", "vending_machine", "parking_tickets"}, SearchMarkType::VendingMachine},
        {{"amenity", "vending_machine", "public_transport_tickets"}, SearchMarkType::VendingMachine},
        {{"amenity", "telephone"}, SearchMarkType::Telephone},
        {{"amenity", "motorcycle_parking"}, SearchMarkType::MotorcycleParking},
        {{"amenity", "bicycle_repair_station"}, SearchMarkType::BicycleRepairStation},
        {{"amenity", "internet_cafe"}, SearchMarkType::InternetCafe},
        {{"shop", "tobacco"}, SearchMarkType::ShopTobacco},
        {{"shop", "newsagent"}, SearchMarkType::ShopNewsagent},
        {{"shop", "second_hand"}, SearchMarkType::ShopSecondHand},
        {{"shop", "chemist"}, SearchMarkType::ShopChemist},
        {{"shop", "ticket"}, SearchMarkType::ShopTicket},
        {{"shop", "copyshop"}, SearchMarkType::ShopCopyshop},
        {{"shop", "photo"}, SearchMarkType::ShopPhoto},
        {{"shop", "music"}, SearchMarkType::ShopMusic},
        {{"shop", "tattoo"}, SearchMarkType::ShopTattoo},
        {{"shop", "garden_centre"}, SearchMarkType::ShopGardenCentre},
        {{"shop", "stationery"}, SearchMarkType::ShopStationery},
        {{"leisure", "dog_park"}, SearchMarkType::DogPark},
        {{"leisure", "sauna"}, SearchMarkType::Sauna},
        {{"leisure", "miniature_golf"}, SearchMarkType::MiniatureGolf},
        {{"leisure", "bowling_alley"}, SearchMarkType::BowlingAlley},
        {{"amenity", "nursing_home"}, SearchMarkType::NursingHome},
        {{"shop"}, SearchMarkType::ShopMall},
        {{"tourism"}, SearchMarkType::Attraction},
        {{"historic"}, SearchMarkType::Monument},
        {{"craft"}, SearchMarkType::ShopDoItYourself},
        {{"shop", "dry_cleaning"}, SearchMarkType::ShopLaundry},
        {{"healthcare", "alternative"}, SearchMarkType::Clinic},
        {{"healthcare", "audiologist"}, SearchMarkType::Clinic},
        {{"healthcare", "blood_donation"}, SearchMarkType::Clinic},
        {{"healthcare", "laboratory"}, SearchMarkType::Clinic},
        {{"healthcare", "optometrist"}, SearchMarkType::Clinic},
        {{"healthcare", "physiotherapist"}, SearchMarkType::Clinic},
        {{"healthcare", "podiatrist"}, SearchMarkType::Clinic},
        {{"healthcare", "psychotherapist"}, SearchMarkType::Clinic},
        {{"healthcare", "sample_collection"}, SearchMarkType::Clinic},
        {{"healthcare", "speech_therapist"}, SearchMarkType::Clinic},
        {{"shop", "tyres"}, SearchMarkType::ShopCarRepair},
        {{"shop", "car_parts"}, SearchMarkType::ShopCarRepair},
        {{"shop", "motorcycle_repair"}, SearchMarkType::ShopCarRepair},
        {{"shop", "computer"}, SearchMarkType::ShopElectronics},
        {{"shop", "hifi"}, SearchMarkType::ShopElectronics},
        {{"shop", "appliance"}, SearchMarkType::ShopElectronics},
        {{"shop", "camera"}, SearchMarkType::ShopElectronics},
        {{"shop", "telecommunication"}, SearchMarkType::ShopMobilePhone},
        {{"shop", "bed"}, SearchMarkType::ShopFurniture},
        {{"shop", "kitchen"}, SearchMarkType::ShopFurniture},
        {{"shop", "interior_decoration"}, SearchMarkType::ShopFurniture},
        {{"shop", "curtain"}, SearchMarkType::ShopFurniture},
        {{"shop", "carpet"}, SearchMarkType::ShopFurniture},
        {{"shop", "boutique"}, SearchMarkType::ShopClothes},
        {{"shop", "fashion_accessories"}, SearchMarkType::ShopClothes},
        {{"shop", "bag"}, SearchMarkType::ShopClothes},
        {{"amenity", "language_school"}, SearchMarkType::School},
        {{"amenity", "music_school"}, SearchMarkType::School},
        {{"amenity", "driving_school"}, SearchMarkType::School},
        {{"amenity", "prep_school"}, SearchMarkType::School},
        {{"amenity", "courthouse"}, SearchMarkType::Townhall},
        {{"office", "government"}, SearchMarkType::Townhall},
        {{"highway", "bus_stop"}, SearchMarkType::BusStation},
        {{"railway", "halt"}, SearchMarkType::TrainStation},
        {{"leisure", "fitness_station"}, SearchMarkType::FitnessCentre},
        {{"shop", "pet_grooming"}, SearchMarkType::ShopPet},
        {{"amenity", "animal_shelter"}, SearchMarkType::ShopPet},
        {{"amenity", "animal_boarding"}, SearchMarkType::ShopPet},
        {{"shop", "beauty", "nails"}, SearchMarkType::ShopBeauty},
        {{"shop", "massage"}, SearchMarkType::ShopBeauty},
        {{"shop", "beauty", "day_spa"}, SearchMarkType::ShopBeauty},
        {{"natural", "volcano"}, SearchMarkType::Peak},
        {{"leisure", "garden"}, SearchMarkType::Park},
        {{"amenity", "car_sharing"}, SearchMarkType::CarRental},
        {{"shop", "outdoor"}, SearchMarkType::ShopSports},
        {{"shop", "fishing"}, SearchMarkType::ShopSports},
        {{"shop", "paint"}, SearchMarkType::ShopDoItYourself},
        {{"shop", "trade"}, SearchMarkType::ShopDoItYourself},
        {{"shop", "variety_store"}, SearchMarkType::ShopMall},
    };

    m_searchMarkTypes.reserve(std::size(table));
    for (auto const & p : table)
      m_searchMarkTypes.push_back({c.GetTypeByPath(p.first), p.second});

    std::sort(m_searchMarkTypes.begin(), m_searchMarkTypes.end());
  }

  std::vector<Type> m_searchMarkTypes;
};

SearchMarkType GetSearchMarkType(uint32_t type)
{
  auto const & checker = SearchMarkTypeChecker::Instance();
  return checker.GetSearchMarkType(type);
}
}  // namespace

SearchMarkPoint::SearchMarkPoint(m2::PointD const & ptOrg)
  : UserMark(ptOrg, UserMark::Type::SEARCH)
  , m_type(SearchMarkType::Default)
  , m_isPreparing(false)
  , m_hasSale(false)
  , m_isSelected(false)
  , m_isVisited(false)
  , m_isAvailable(true)
{}

m2::PointD SearchMarkPoint::GetPixelOffset() const
{
  return {0.0, 6.0};
}

drape_ptr<df::UserPointMark::SymbolNameZoomInfo> SearchMarkPoint::GetSymbolNames() const
{
  auto const symbolName = GetSymbolName();
  if (symbolName == nullptr)
    return nullptr;

  auto symbolZoomInfo = make_unique_dp<SymbolNameZoomInfo>();
  symbolZoomInfo->emplace(1 /*kWorldZoomLevel*/, *symbolName);
  return symbolZoomInfo;
}

drape_ptr<df::UserPointMark::SymbolOffsets> SearchMarkPoint::GetSymbolOffsets() const
{
  m2::PointF offset(0, 1);
  return make_unique_dp<SymbolOffsets>(static_cast<size_t>(scales::UPPER_STYLE_SCALE), offset);
}

bool SearchMarkPoint::IsMarkAboveText() const
{
  return true;
}

float SearchMarkPoint::GetSymbolOpacity() const
{
  if (!m_isAvailable)
    return kOutOfFiltersSymbolOpacity;
  return m_isVisited ? kVisitedSymbolOpacity : 1.0f;
}

df::ColorConstant SearchMarkPoint::GetColorConstant() const
{
  return kColorConstant;
}

int SearchMarkPoint::GetMinTitleZoom() const
{
  return scales::GetUpperCountryScale();
}

df::DepthLayer SearchMarkPoint::GetDepthLayer() const
{
  return df::DepthLayer::SearchMarkLayer;
}

void SearchMarkPoint::SetFoundFeature(FeatureID const & feature)
{
  SetAttributeValue(m_featureID, feature);
}

void SearchMarkPoint::SetMatchedName(std::string const & name)
{
  SetAttributeValue(m_matchedName, name);
}

void SearchMarkPoint::SetFromType(uint32_t type)
{
  SetAttributeValue(m_type, GetSearchMarkType(type));
}

void SearchMarkPoint::SetNotFoundType()
{
  SetAttributeValue(m_type, SearchMarkType::NotFound);
}

#define SET_BOOL_ATTRIBUTE(dest, src) \
  if (dest != src)                    \
  {                                   \
    dest = src;                       \
    SetDirty();                       \
  }

void SearchMarkPoint::SetPreparing(bool isPreparing)
{
  SET_BOOL_ATTRIBUTE(m_isPreparing, isPreparing);
}

void SearchMarkPoint::SetSale(bool hasSale)
{
  SET_BOOL_ATTRIBUTE(m_hasSale, hasSale);
}

void SearchMarkPoint::SetSelected(bool isSelected)
{
  SET_BOOL_ATTRIBUTE(m_isSelected, isSelected);
}

void SearchMarkPoint::SetVisited(bool isVisited)
{
  SET_BOOL_ATTRIBUTE(m_isVisited, isVisited);
}

void SearchMarkPoint::SetAvailable(bool isAvailable)
{
  SET_BOOL_ATTRIBUTE(m_isAvailable, isAvailable);
}

#undef SET_BOOL_ATTRIBUTE

void SearchMarkPoint::SetReason(std::string const & reason)
{
  SetAttributeValue(m_reason, reason);
}

bool SearchMarkPoint::IsSelected() const
{
  return m_isSelected;
}

bool SearchMarkPoint::IsAvailable() const
{
  return m_isAvailable;
}

std::string const & SearchMarkPoint::GetReason() const
{
  return m_reason;
}

bool SearchMarkPoint::HasReason() const
{
  return !m_reason.empty();
}

std::string const * SearchMarkPoint::GetSymbolName() const
{
  std::string const * symbolName = nullptr;

  if (!SearchMarks::HaveSizes())
    return symbolName;

  if (m_type >= SearchMarkType::Count)
  {
    ASSERT(false, (m_type));
    symbolName = &GetSymbol(SearchMarkType::Default);
  }
  else
  {
    symbolName = &GetSymbol(m_type);
  }

  if (!SearchMarks::GetSize(*symbolName))
  {
    ASSERT(false, (*symbolName));
    symbolName = nullptr;
  }

  return symbolName;
}

// static
std::map<std::string, m2::PointF> SearchMarks::s_markSizes;

SearchMarks::SearchMarks() : m_bmManager(nullptr) {}

void SearchMarks::SetDrapeEngine(ref_ptr<df::DrapeEngine> engine)
{
  m_drapeEngine.Set(engine);
  if (engine == nullptr)
    return;

  std::vector<std::string> symbols;
  for (uint8_t t = 0; t < SearchMarkType::Count; ++t)
    symbols.push_back(GetSymbol(static_cast<SearchMarkType>(t)));

  base::SortUnique(symbols);

  m_drapeEngine.SafeCall(&df::DrapeEngine::RequestSymbolsSize, symbols,
                         [this](std::map<std::string, m2::PointF> && sizes)
  {
    GetPlatform().RunTask(Platform::Thread::Gui, [this, sizes = std::move(sizes)]() mutable
    {
      s_markSizes = std::move(sizes);
      UpdateMaxDimension();
    });
  });
}

void SearchMarks::SetBookmarkManager(BookmarkManager * bmManager)
{
  m_bmManager = bmManager;
}

m2::PointD SearchMarks::GetMaxDimension(ScreenBase const & modelView) const
{
  return m_maxDimension * modelView.GetScale();
}

// static
std::optional<m2::PointD> SearchMarks::GetSize(std::string const & symbolName)
{
  auto const it = s_markSizes.find(symbolName);
  if (it == s_markSizes.end())
    return {};
  return m2::PointD(it->second);
}

void SearchMarks::SetPreparingState(std::vector<FeatureID> const & features, bool isPreparing)
{
  if (features.empty())
    return;

  ProcessMarks([&features, isPreparing](SearchMarkPoint * mark) -> base::ControlFlow
  {
    ASSERT(std::is_sorted(features.cbegin(), features.cend()), ());
    if (std::binary_search(features.cbegin(), features.cend(), mark->GetFeatureID()))
      mark->SetPreparing(isPreparing);
    return base::ControlFlow::Continue;
  });
}

void SearchMarks::SetSales(std::vector<FeatureID> const & features, bool hasSale)
{
  if (features.empty())
    return;

  ProcessMarks([&features, hasSale](SearchMarkPoint * mark) -> base::ControlFlow
  {
    ASSERT(std::is_sorted(features.cbegin(), features.cend()), ());
    if (std::binary_search(features.cbegin(), features.cend(), mark->GetFeatureID()))
      mark->SetSale(hasSale);
    return base::ControlFlow::Continue;
  });
}

bool SearchMarks::IsThereSearchMarkForFeature(FeatureID const & featureId) const
{
  for (auto const markId : m_bmManager->GetUserMarkIds(UserMark::Type::SEARCH))
    if (m_bmManager->GetUserMark(markId)->GetFeatureID() == featureId)
      return true;
  return false;
}

void SearchMarks::OnActivate(FeatureID const & featureId)
{
  m_selectedFeature = featureId;
  m_visitedSearchMarks.erase(featureId);
  ProcessMarks([&featureId](SearchMarkPoint * mark) -> base::ControlFlow
  {
    if (featureId != mark->GetFeatureID())
      return base::ControlFlow::Continue;
    mark->SetVisited(false);
    mark->SetSelected(true);
    return base::ControlFlow::Break;
  });
}

void SearchMarks::OnDeactivate(FeatureID const & featureId)
{
  m_selectedFeature = {};
  m_visitedSearchMarks.insert(featureId);
  ProcessMarks([&featureId](SearchMarkPoint * mark) -> base::ControlFlow
  {
    if (featureId != mark->GetFeatureID())
      return base::ControlFlow::Continue;
    mark->SetVisited(true);
    mark->SetSelected(false);
    return base::ControlFlow::Break;
  });
}

/*
void SearchMarks::SetUnavailable(SearchMarkPoint & mark, std::string const & reasonKey)
{
  {
    std::scoped_lock<std::mutex> lock(m_lock);
    m_unavailable.insert_or_assign(mark.GetFeatureID(), reasonKey);
  }
  mark.SetAvailable(false);
  mark.SetReason(localisation::TranslatedInterfaceText(reasonKey));
}

void SearchMarks::SetUnavailable(std::vector<FeatureID> const & features,
                                 std::string const & reasonKey)
{
  if (features.empty())
    return;

  ProcessMarks([this, &features, &reasonKey](SearchMarkPoint * mark) -> base::ControlFlow
  {
    ASSERT(std::is_sorted(features.cbegin(), features.cend()), ());
    if (std::binary_search(features.cbegin(), features.cend(), mark->GetFeatureID()))
      SetUnavailable(*mark, reasonKey);
    return base::ControlFlow::Continue;
  });
}

bool SearchMarks::IsUnavailable(FeatureID const & id) const
{
  std::scoped_lock<std::mutex> lock(m_lock);
  return m_unavailable.find(id) != m_unavailable.cend();
}
*/

void SearchMarks::SetVisited(FeatureID const & id)
{
  m_visitedSearchMarks.insert(id);
}

bool SearchMarks::IsVisited(FeatureID const & id) const
{
  return m_visitedSearchMarks.find(id) != m_visitedSearchMarks.cend();
}

void SearchMarks::SetSelected(FeatureID const & id)
{
  m_selectedFeature = id;
}

bool SearchMarks::IsSelected(FeatureID const & id) const
{
  return id == m_selectedFeature;
}

void SearchMarks::ClearTrackedProperties()
{
  //  {
  //    std::scoped_lock<std::mutex> lock(m_lock);
  //    m_unavailable.clear();
  //  }
  m_selectedFeature = {};
}

void SearchMarks::ProcessMarks(std::function<base::ControlFlow(SearchMarkPoint *)> && processor) const
{
  if (m_bmManager == nullptr || processor == nullptr)
    return;

  auto editSession = m_bmManager->GetEditSession();
  for (auto markId : m_bmManager->GetUserMarkIds(UserMark::Type::SEARCH))
  {
    auto * mark = editSession.GetMarkForEdit<SearchMarkPoint>(markId);
    if (processor(mark) == base::ControlFlow::Break)
      break;
  }
}

void SearchMarks::UpdateMaxDimension()
{
  // Use only generic search mark as 'filter' dimension. Users claim about missing results.
  // https://github.com/organicmaps/organicmaps/issues/2070
  auto const it = s_markSizes.find(kSymbols[SearchMarkType::Default]);
  if (it != s_markSizes.end())
    m_maxDimension = it->second;
}
