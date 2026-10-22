// App — sets up routing between the pages.
//
// Routes:
//   /              → SessionList (home page, shows all sessions)
//   /sessions/:id  → SessionPage (one session: goal, files, diff and explanations)
//   /sessions/:id/file?path=... → the same page, with that file selected
//   /concepts      → ConceptsPage (concepts across the project's sessions)
//   /review        → ReviewPage (understood changes that are due for review)
//   One route with an optional "file" part, so moving between files keeps the page
//   (and anything already explained) instead of loading it again.
//
// The project path is passed as a URL query param: ?project=/path/to/project
// Both pages can read it from the URL.

import { BrowserRouter, Routes, Route } from 'react-router-dom';
import SessionList from './pages/SessionList';
import SessionPage from './pages/SessionPage';
import ConceptsPage from './pages/ConceptsPage';
import ReviewPage from './pages/ReviewPage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<SessionList />} />
        <Route path="/sessions/:id/file?" element={<SessionPage />} />
        <Route path="/concepts" element={<ConceptsPage />} />
        <Route path="/review" element={<ReviewPage />} />
      </Routes>
    </BrowserRouter>
  );
}
