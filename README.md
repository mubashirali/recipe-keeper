# Recipe Keeper Pro

## Inspiration
Implementation of the idea generated from Gemma 4 On-Device Models using only vibe coding.

## Project Overview
Recipe Keeper Pro is a recipe management application designed to evolve from a Minimum Viable Product (MVP) to a feature-rich, intelligent application. The app leverages modern technologies including Kotlin/Java or Flutter for frontend, Firebase for backend services, Room for local storage, and Google Cloud Natural Language API for AI-powered ingredient extraction.

## Technology Stack
- **Primary Language:** Kotlin or Java (for Android Native) / Flutter (for Cross-Platform)
- **Backend:** Firebase (Authentication, Firestore Database)
- **Local Storage:** Room Persistence Library (for offline access)
- **AI/NLP:** Google Cloud Natural Language API (for ingredient extraction and dietary parsing)

## Development Phases

### Phase 0: Foundation & Design (Estimated: 2 Weeks)
**Goal:** Establish the core architecture and User Flow (Wireframing).

**Features to Implement:**
- **User Authentication:** Implement Firebase Auth (Email/Password, Google Sign-In).
- **Data Model Design (Firestore):** Define the core data structure:
  - `User` Collection
  - `Recipe` Collection (Title, Description, Ingredients Array, Instructions Array, Prep Time, Servings)
  - `Ingredient` Sub-Collection (Name, Quantity, Unit, Optional: Dietary Tag)
- **UI/UX Wireframing:** Create low-fidelity wireframes for the main screens: Dashboard, Recipe Creator, Recipe Viewer.
- **Basic Setup:** Set up the repository structure and basic database connection tests.

### Phase 1: Minimum Viable Product (MVP) (Estimated: 6 Weeks)
**Goal:** Allow users to successfully create, view, and store recipes offline.

**Features to Implement:**
1. **Recipe Creation (Manual Input):**
   - A robust form for entering recipe details.
   - Ability to add ingredients line-by-line (Quantity, Unit, Name).
   - Instructions list (numbered steps).
2. **Recipe Viewing:**
   - Displaying all stored recipes in a scrollable feed.
   - Detail view: Ingredients list separated from instructions.
3. **Basic Search/Filtering:**
   - Search by Recipe Name (Exact Match).
   - Filter by user-defined tags (e.g., Dinner, Quick Meal).
4. **Data Persistence:**
   - Ensure all data persists locally (Offline First design).
   - Cloud backup/sync capability.

**Phase 1 Success Metrics:**
- A user can successfully sign up and manually input 5 unique recipes.
- The app functions fully offline.
- The user can retrieve all 5 recipes correctly.

### Phase 2: Intelligence & Usability Upgrade (V2.0) (Estimated: 8 Weeks)
**Goal:** Automate data entry, improve user experience, and add social features.

**Features to Implement:**
1. **Advanced Ingredient Parsing (NLP):**
   - Implement the Natural Language API: Allow users to input unstructured text (e.g., "1 cup of flour, 2 eggs, and a pinch of salt") and automatically populate the Quantity, Unit, and Ingredient fields.
2. **Search Enhancement:**
   - **Ingredient Search:** Search recipes based only on a single ingredient name.
   - **Dietary Filtering:** Implement pre-set filters (Vegan, Gluten-Free, High Protein).
3. **Meal Planner:**
   - A calendar view where users can assign recipes to specific dates/meals (Breakfast, Lunch, Dinner).
   - Feature Enhancement: Display the full nutrient summary for the day's planned meals.
4. **User Profile & Collaboration:**
   - User profile customization.
   - Ability to share recipes publicly or privately with friends within the app.

**Phase 2 Success Metrics:**
- A user can paste raw text and correctly parse 90% of ingredients (Quantity/Unit/Name).
- The Meal Planner allows the scheduling of 3 days of meals.
- The app supports cloud sync between multiple devices.

### Phase 3: Polish & Scaling (V3.0) (Estimated: Ongoing)
**Goal:** Introduce advanced features, monetization potential, and robust scaling.

**Features to Implement:**
1. **Shopping List Generator:**
   - Based on the scheduled Meal Plan, generate a consolidated shopping list (grouping ingredients by store aisle/category).
   - Advanced: Quantity aggregation (if 3 recipes need 1 cup of milk, the list only shows 1 item, 3 cups total).
2. **Dietary Management & Scaling:**
   - Advanced feature to modify a recipe's servings, recalculating all ingredient quantities automatically.
   - Full dietary profile management for the user and recipes (e.g., linking specific allergy warnings).
3. **AI Suggestion Engine:** "What's in the Pantry?" — User inputs a list of available ingredients, and the app suggests recipes they can make right now.
4. **User Experience:**
   - Voice input recording for instructions.
   - Dark Mode implementation.

**Phase 3 Success Metrics:**
- The Shopping List Generator accurately aggregates ingredients across multiple recipes.
- The "Pantry Suggestions" feature successfully suggests a recipe based on 5+ random ingredients.
- High user retention rate and positive user reviews regarding the usability improvements.

## Summary Timeline View

| Phase | Focus        | Key Milestone                | Time Estimate | Primary Goal                  |
| :---- | :----------- | :--------------------------- | :------------ | :---------------------------- |
| **0** | Foundation   | Wireframes & Data Model      | 2 Weeks       | Architecture Setup            |
| **1** | MVP          | Manual Recipe CRUD (Offline) | 6 Weeks       | Core Functionality            |
| **2** | Intelligence | NLP Parsing & Meal Planner   | 8 Weeks       | Smart Features & UX           |
| **3** | Scale        | Shopping List & Pantry AI    | Ongoing       | Feature Parity & Optimization |

---
*This README summarizes the project roadmap and development plan for Recipe Keeper Pro.*