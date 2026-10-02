// App — sets up routing between the two pages.
//
// Routes:
//   /              → SessionList (home page, shows all sessions)
//   /sessions/:id  → SessionDetail (one session's changes and diffs)
//
// The project path is passed as a URL query param: ?project=/path/to/project
// Both pages can read it from the URL.

import { BrowserRouter, Routes, Route } from 'react-router-dom';
import SessionList from './pages/SessionList';
import SessionDetail from './pages/SessionDetail';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<SessionList />} />
        <Route path="/sessions/:id" element={<SessionDetail />} />
      </Routes>
    </BrowserRouter>
  );
}
