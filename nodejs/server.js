const express = require("express")
const mongoose = require("mongoose")
const mysql = require("mysql2/promise")
const cors = require("cors")
const bodyParser = require("body-parser")
const fs = require("fs")
const path = require("path")
const snmp = require("snmp-native")

// Create Express app
const app = express()
const port = 8080

// Middleware
app.use(cors())
app.use(bodyParser.json())
app.use(bodyParser.urlencoded({ extended: true }))

// MySQL connection pool
let mysqlPool

// Initialize MySQL
async function initMySQL() {
  try {
    // Create database if it doesn't exist
    const tempConnection = await mysql.createConnection({
      host: "mysql",
      user: "root",
      password: "password",
    })

    await tempConnection.execute("CREATE DATABASE IF NOT EXISTS image_db")
    await tempConnection.end()

    // Create connection pool
    mysqlPool = mysql.createPool({
      host: "mysql",
      user: "root",
      password: "password",
      database: "image_db",
      waitForConnections: true,
      connectionLimit: 10,
      queueLimit: 0,
    })

    // Create tables
    await mysqlPool.execute(`
      CREATE TABLE IF NOT EXISTS processed_images (
        id INT AUTO_INCREMENT PRIMARY KEY,
        job_id VARCHAR(36) NOT NULL,
        image_data LONGBLOB NOT NULL,
        created_at DATETIME NOT NULL
      )
    `)

    console.log("MySQL initialized successfully")
  } catch (error) {
    console.error("Error initializing MySQL:", error)
    process.exit(1)
  }
}

// Initialize MongoDB
async function initMongoDB() {
  try {
    await mongoose.connect("mongodb://mongo:27017/node_stats", {
      useNewUrlParser: true,
      useUnifiedTopology: true,
    })

    console.log("MongoDB connected successfully")
  } catch (error) {
    console.error("Error connecting to MongoDB:", error)
    process.exit(1)
  }
}

// Define MongoDB schema
const nodeStatsSchema = new mongoose.Schema({
  hostname: String,
  os: String,
  cpuUsage: Number,
  ramUsage: Number,
  status: String,
  timestamp: { type: Date, default: Date.now },
})

const NodeStats = mongoose.model("NodeStats", nodeStatsSchema)

// Routes
app.get("/api/images/:id", async (req, res) => {
  try {
    const [rows] = await mysqlPool.execute("SELECT image_data FROM processed_images WHERE job_id = ?", [req.params.id])

    if (rows.length === 0) {
      return res.status(404).json({ message: "Image not found" })
    }

    const imageData = rows[0].image_data

    res.setHeader("Content-Type", "image/bmp")
    res.send(imageData)
  } catch (error) {
    console.error("Error retrieving image:", error)
    res.status(500).json({ message: "Error retrieving image" })
  }
})

app.get("/api/stats", async (req, res) => {
  try {
    // Get latest stats for each node
    const stats = await NodeStats.aggregate([
      { $sort: { timestamp: -1 } },
      {
        $group: {
          _id: "$hostname",
          hostname: { $first: "$hostname" },
          os: { $first: "$os" },
          cpuUsage: { $first: "$cpuUsage" },
          ramUsage: { $first: "$ramUsage" },
          status: { $first: "$status" },
          timestamp: { $first: "$timestamp" },
        },
      },
    ])

    res.json({ nodes: stats })
  } catch (error) {
    console.error("Error retrieving stats:", error)
    res.status(500).json({ message: "Error retrieving stats" })
  }
})

app.post("/api/notify", async (req, res) => {
  try {
    const { jobId, status } = req.body

    if (!jobId || !status) {
      return res.status(400).json({ message: "Missing required fields" })
    }

    // Update job status in backend
    const response = await fetch("http://frontend-backend:7000/api/status/update", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ jobId, status, imageId: jobId }),
    })

    if (!response.ok) {
      throw new Error(`Failed to update job status: ${response.statusText}`)
    }

    res.json({ message: "Notification received and processed" })
  } catch (error) {
    console.error("Error processing notification:", error)
    res.status(500).json({ message: "Error processing notification" })
  }
})

// SNMP collection function
async function collectSNMPStats() {
  const nodes = [
    { hostname: "frontend-backend", ip: "frontend-backend" },
    { hostname: "rabbitmq", ip: "rabbitmq" },
    { hostname: "java-mdb", ip: "java-mdb" },
    { hostname: "openmpi-worker", ip: "openmpi-worker" },
    { hostname: "nodejs-db", ip: "nodejs-db" },
  ]

  for (const node of nodes) {
    try {
      // Simulate SNMP data collection (in a real scenario, we would use actual SNMP queries)
      const cpuUsage = Math.floor(Math.random() * 100)
      const ramUsage = Math.floor(Math.random() * 100)

      // Save to MongoDB
      await NodeStats.create({
        hostname: node.hostname,
        os: "Ubuntu 24.04 LTS",
        cpuUsage,
        ramUsage,
        status: "online",
      })

      console.log(`Collected stats for ${node.hostname}`)
    } catch (error) {
      console.error(`Error collecting stats for ${node.hostname}:`, error)
    }
  }
}

// Start server
async function startServer() {
  try {
    await initMySQL()
    await initMongoDB()

    app.listen(port, () => {
      console.log(`Server running on port ${port}`)

      // Start SNMP collection every 30 seconds
      setInterval(collectSNMPStats, 30000)

      // Initial SNMP collection
      collectSNMPStats()
    })
  } catch (error) {
    console.error("Error starting server:", error)
    process.exit(1)
  }
}

startServer()