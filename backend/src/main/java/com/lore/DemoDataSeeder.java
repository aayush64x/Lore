package com.lore;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import com.lore.change.Explanation;
import com.lore.change.ExplanationRepository;
import com.lore.session.Session;
import com.lore.session.SessionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DemoDataSeeder {

    private final SessionRepository sessionRepository;
    private final ChangeRepository changeRepository;
    private final ExplanationRepository explanationRepository;

    public DemoDataSeeder(SessionRepository sessionRepository,
                          ChangeRepository changeRepository,
                          ExplanationRepository explanationRepository) {
        this.sessionRepository = sessionRepository;
        this.changeRepository = changeRepository;
        this.explanationRepository = explanationRepository;
    }

    @PostConstruct
    public void seed() {
        System.out.println("DemoDataSeeder: count=" + sessionRepository.count());
        if (sessionRepository.findByProjectPathAndStatusOrderByStartedAtDesc(
                "/Users/aayushnakarmi/Desktop/Projects/Lore",
                Session.SessionStatus.COMPLETE).size() > 0) {
            System.out.println("DemoDataSeeder: demo data already exists, skipping");
            return;
        }

        Session session = new Session(UUID.randomUUID(), "/Users/aayushnakarmi/Desktop/Projects/Lore", "abc123");
        session.setReason("Add user authentication");
        session.setSummary("Added JWT-based authentication with login/signup endpoints, password hashing, and middleware to protect API routes.");
        session.setSummaryStatus(Session.SummaryStatus.COMPLETE);
        session.setStatus(Session.SessionStatus.COMPLETE);
        session.setStopRef("def456");
        session.setStoppedAt(session.getStartedAt().plusSeconds(3600));
        sessionRepository.save(session);

        // Change 1: new auth middleware
        Change c1 = new Change(UUID.randomUUID(), session, "src/middleware/auth.ts", Change.ChangeType.ADDED);
        c1.setNewStart(1);
        c1.setNewCount(18);
        c1.setDisplayOrder(0);
        c1.setDescription("Add JWT authentication middleware");
        c1.setDiffContent(
            "@@ -0,0 +1,18 @@\n" +
            "+import jwt from 'jsonwebtoken';\n" +
            "+import { Request, Response, NextFunction } from 'express';\n" +
            "+\n" +
            "+const SECRET = process.env.JWT_SECRET || 'dev-secret';\n" +
            "+\n" +
            "+export interface AuthRequest extends Request {\n" +
            "+  userId?: string;\n" +
            "+}\n" +
            "+\n" +
            "+export function authMiddleware(req: AuthRequest, res: Response, next: NextFunction) {\n" +
            "+  const token = req.headers.authorization?.split(' ')[1];\n" +
            "+  if (!token) {\n" +
            "+    return res.status(401).json({ error: 'No token provided' });\n" +
            "+  }\n" +
            "+  const decoded = jwt.verify(token, SECRET) as { userId: string };\n" +
            "+  req.userId = decoded.userId;\n" +
            "+  next();\n" +
            "+}"
        );
        changeRepository.save(c1);

        // Explanation for change 1
        Explanation e1 = new Explanation(UUID.randomUUID(), c1);
        e1.setOverview("This file creates Express middleware that checks for a valid JWT token on incoming requests.");
        e1.setSteps("[" +
            "{\"text\":\"Import the JWT library and Express types needed for middleware.\",\"diff_line_start\":2,\"diff_line_end\":3}," +
            "{\"text\":\"Define the secret key used to sign and verify tokens. Falls back to a dev secret if the environment variable isn't set.\",\"diff_line_start\":5,\"diff_line_end\":5}," +
            "{\"text\":\"Extend the Express Request type to include a userId field that the middleware will attach.\",\"diff_line_start\":7,\"diff_line_end\":9}," +
            "{\"text\":\"Extract the Bearer token from the Authorization header, verify it, and attach the decoded userId to the request object.\",\"diff_line_start\":11,\"diff_line_end\":18}" +
            "]");
        e1.setConcepts("[" +
            "{\"name\":\"JWT (JSON Web Token)\",\"meaning\":\"A compact token format for securely transmitting claims between parties. The server signs it with a secret; clients send it back to prove identity.\"}," +
            "{\"name\":\"Middleware\",\"meaning\":\"A function that runs between receiving a request and sending a response. It can modify the request, reject it, or pass it along to the next handler.\"}" +
            "]");
        e1.setStatus(Explanation.ExplanationStatus.COMPLETE);
        explanationRepository.save(e1);

        // Change 2: login route
        Change c2 = new Change(UUID.randomUUID(), session, "src/routes/auth.ts", Change.ChangeType.ADDED);
        c2.setNewStart(1);
        c2.setNewCount(25);
        c2.setDisplayOrder(1);
        c2.setDescription("Add login and signup endpoints");
        c2.setDiffContent(
            "@@ -0,0 +1,25 @@\n" +
            "+import { Router } from 'express';\n" +
            "+import bcrypt from 'bcrypt';\n" +
            "+import jwt from 'jsonwebtoken';\n" +
            "+import { db } from '../database';\n" +
            "+\n" +
            "+const router = Router();\n" +
            "+const SECRET = process.env.JWT_SECRET || 'dev-secret';\n" +
            "+\n" +
            "+router.post('/signup', async (req, res) => {\n" +
            "+  const { email, password } = req.body;\n" +
            "+  const hash = await bcrypt.hash(password, 10);\n" +
            "+  const user = await db.user.create({ email, password: hash });\n" +
            "+  const token = jwt.sign({ userId: user.id }, SECRET);\n" +
            "+  res.json({ token });\n" +
            "+});\n" +
            "+\n" +
            "+router.post('/login', async (req, res) => {\n" +
            "+  const { email, password } = req.body;\n" +
            "+  const user = await db.user.findByEmail(email);\n" +
            "+  if (!user || !await bcrypt.compare(password, user.password)) {\n" +
            "+    return res.status(401).json({ error: 'Invalid credentials' });\n" +
            "+  }\n" +
            "+  const token = jwt.sign({ userId: user.id }, SECRET);\n" +
            "+  res.json({ token });\n" +
            "+});"
        );
        changeRepository.save(c2);

        // Change 3: modify existing routes to use auth
        Change c3 = new Change(UUID.randomUUID(), session, "src/routes/api.ts", Change.ChangeType.MODIFIED);
        c3.setOldStart(1);
        c3.setOldCount(5);
        c3.setNewStart(1);
        c3.setNewCount(8);
        c3.setDisplayOrder(2);
        c3.setDescription("Protect API routes with auth middleware");
        c3.setDiffContent(
            "@@ -1,5 +1,8 @@\n" +
            " import { Router } from 'express';\n" +
            "+import { authMiddleware } from '../middleware/auth';\n" +
            " \n" +
            " const router = Router();\n" +
            " \n" +
            "+router.use(authMiddleware);\n" +
            "+\n" +
            " router.get('/projects', async (req, res) => {"
        );
        c3.setUnderstood(true);
        changeRepository.save(c3);

        // --- Session 2 ---
        Session session2 = new Session(UUID.randomUUID(), "/Users/aayushnakarmi/Desktop/Projects/Lore", "xyz789");
        session2.setReason("Fix pagination bug in project list");
        session2.setSummary("Fixed off-by-one error in pagination logic that caused the last page to show one extra item.");
        session2.setSummaryStatus(Session.SummaryStatus.COMPLETE);
        session2.setStatus(Session.SessionStatus.COMPLETE);
        session2.setStopRef("xyz790");
        session2.setStoppedAt(session2.getStartedAt().plusSeconds(1200));
        sessionRepository.save(session2);

        Change c4 = new Change(UUID.randomUUID(), session2, "src/services/ProjectService.ts", Change.ChangeType.MODIFIED);
        c4.setOldStart(42);
        c4.setOldCount(4);
        c4.setNewStart(42);
        c4.setNewCount(4);
        c4.setDisplayOrder(0);
        c4.setDescription("Fix off-by-one in pagination offset calculation");
        c4.setDiffContent(
            "@@ -42,4 +42,4 @@\n" +
            " function paginate(items: any[], page: number, pageSize: number) {\n" +
            "-  const start = page * pageSize;\n" +
            "-  const end = start + pageSize + 1;\n" +
            "+  const start = (page - 1) * pageSize;\n" +
            "+  const end = start + pageSize;\n" +
            "   return items.slice(start, end);\n" +
            " }"
        );
        changeRepository.save(c4);

        System.out.println("Demo data seeded: 2 sessions, 4 changes, 1 explanation");
    }
}
