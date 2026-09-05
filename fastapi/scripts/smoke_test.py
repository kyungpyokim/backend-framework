import subprocess
import time
import sys
import httpx

def main():
    print("Starting Uvicorn server in background...")
    proc = subprocess.Popen(
        ["uv", "run", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8765"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )

    try:
        # Wait for server to start
        url = "http://127.0.0.1:8765/health"
        for _ in range(20):
            time.sleep(0.5)
            try:
                resp = httpx.get(url, timeout=1.0)
                if resp.status_code == 200:
                    print(f"Smoke test PASSED! Response: {resp.json()}")
                    return 0
            except Exception:
                continue

        print("Smoke test FAILED: Could not connect to server.")
        return 1
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            proc.kill()
        print("Server stopped cleanly.")

if __name__ == "__main__":
    sys.exit(main())
