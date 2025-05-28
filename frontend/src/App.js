"use client"

import { useState, useEffect } from "react"
import axios from "axios"
import "./App.css"

const API_URL = "http://localhost:7000/api"

function App() {
  const [selectedFile, setSelectedFile] = useState(null)
  const [key, setKey] = useState("")
  const [operation, setOperation] = useState("encrypt")
  const [mode, setMode] = useState("ECB")
  const [status, setStatus] = useState("")
  const [resultImageUrl, setResultImageUrl] = useState("")
  const [isLoading, setIsLoading] = useState(false)
  const [systemStats, setSystemStats] = useState(null)

  useEffect(() => {
    // Poll for system stats every 5 seconds
    const interval = setInterval(() => {
      fetchSystemStats()
    }, 5000)

    return () => clearInterval(interval)
  }, [])

  const fetchSystemStats = async () => {
    try {
      const response = await axios.get("http://localhost:8080/api/stats")
      setSystemStats(response.data)
    } catch (error) {
      console.error("Error fetching system stats:", error)
    }
  }

  const handleFileChange = (event) => {
    setSelectedFile(event.target.files[0])
  }

  const handleKeyChange = (event) => {
    setKey(event.target.value)
  }

  const handleOperationChange = (event) => {
    setOperation(event.target.value)
  }

  const handleModeChange = (event) => {
    setMode(event.target.value)
  }

  const handleSubmit = async (event) => {
    event.preventDefault()

    if (!selectedFile || !key) {
      setStatus("Please select a file and enter a key")
      return
    }

    setIsLoading(true)
    setStatus("Processing...")

    const formData = new FormData()
    formData.append("image", selectedFile)
    formData.append("key", key)
    formData.append("operation", operation)
    formData.append("mode", mode)

    try {
      const response = await axios.post(`${API_URL}/process`, formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      })

      setStatus(`Operation completed successfully! Job ID: ${response.data.jobId}`)

      // Start polling for the result
      pollForResult(response.data.jobId)
    } catch (error) {
      setStatus(`Error: ${error.response?.data?.message || error.message}`)
      setIsLoading(false)
    }
  }

  const pollForResult = async (jobId) => {
    const checkResult = async () => {
      try {
        const response = await axios.get(`${API_URL}/status/${jobId}`)

        if (response.data.status === "completed") {
          setStatus("Image processing completed!")
          setResultImageUrl(`http://localhost:8080/api/images/${response.data.imageId}`)
          setIsLoading(false)
          return true
        }

        return false
      } catch (error) {
        console.error("Error checking job status:", error)
        return false
      }
    }

    const poll = async () => {
      const done = await checkResult()
      if (!done) {
        setTimeout(poll, 2000)
      }
    }

    poll()
  }

  return (
    <div className="app-container">
      <h1>Image Encryption/Decryption System</h1>

      <div className="main-content">
        <div className="form-container">
          <h2>Upload Image</h2>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>BMP Image:</label>
              <input type="file" accept=".bmp" onChange={handleFileChange} />
            </div>

            <div className="form-group">
              <label>AES Key:</label>
              <input
                type="text"
                value={key}
                onChange={handleKeyChange}
                placeholder="Enter 16, 24, or 32 character key"
              />
            </div>

            <div className="form-group">
              <label>Operation:</label>
              <select value={operation} onChange={handleOperationChange}>
                <option value="encrypt">Encrypt</option>
                <option value="decrypt">Decrypt</option>
              </select>
            </div>

            <div className="form-group">
              <label>Mode:</label>
              <select value={mode} onChange={handleModeChange}>
                <option value="ECB">ECB</option>
                <option value="CBC">CBC</option>
              </select>
            </div>

            <button type="submit" disabled={isLoading}>
              {isLoading ? "Processing..." : "Process Image"}
            </button>
          </form>

          <div className="status-message">{status && <p>{status}</p>}</div>
        </div>

        <div className="result-container">
          {resultImageUrl && (
            <div>
              <h2>Processed Image</h2>
              <img src={resultImageUrl || "/placeholder.svg"} alt="Processed" className="result-image" />
              <a href={resultImageUrl} download className="download-button">
                Download Image
              </a>
            </div>
          )}
        </div>
      </div>

      {systemStats && (
        <div className="system-stats">
          <h2>System Statistics</h2>
          <div className="stats-grid">
            {systemStats.nodes.map((node, index) => (
              <div key={index} className="stat-card">
                <h3>Node: {node.hostname}</h3>
                <p>OS: {node.os}</p>
                <p>CPU Usage: {node.cpuUsage}%</p>
                <p>RAM Usage: {node.ramUsage}%</p>
                <p>Status: {node.status}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default App
